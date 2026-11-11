/**
 * MedocMaman — Zero-Cost Cloud Watchdog & Google Sheet Admin Backend (Google Apps Script)
 *
 * Attach this script to a Google Sheet with 3 tabs:
 *   1. "Medications"
 *   2. "Exams"
 *   3. "Config_And_Contacts"
 *
 * Required Script Properties (Project Settings > Script Properties):
 *   - FIREBASE_PROJECT_ID : Your Firebase Project ID (e.g. "medoc-maman-prod")
 *   - FIREBASE_CLIENT_EMAIL : Service Account client_email
 *   - FIREBASE_PRIVATE_KEY : Service Account private_key (with \n newlines)
 */

const SILENT_FAILURE_MINUTES = 10;
const UNANSWERED_FAILURE_MINUTES = 35;

/**
 * 1. Sheet-to-Firestore Sync (Run on Edit or manually from menu)
 * Pushes all rows from "Medications" and "Exams" tabs to Firestore and sends a silent FCM sync message.
 */
function syncSheetToFirestore() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const medSheet = ss.getSheetByName("Medications");
  const examSheet = ss.getSheetByName("Exams");

  if (medSheet) {
    const medRows = getSheetObjects_(medSheet);
    medRows.forEach(row => {
      if (!row.id) return;
      const scheduledTimes = String(row.scheduled_times || "08:00")
        .split(",")
        .map(s => s.trim())
        .filter(Boolean);

      const docData = {
        id: String(row.id),
        name_fr: String(row.name_fr || "Médicament"),
        dosage_fr: String(row.dosage_fr || "Un comprimé"),
        meal_timing_fr: String(row.meal_timing_fr || ""),
        visual_description_fr: String(row.visual_description_fr || ""),
        image_url: String(row.image_url || ""),
        local_image_res: String(row.local_image_res || "pill_white_round"),
        scheduled_times: scheduledTimes,
        on_missed_instruction_fr: String(
          row.on_missed_instruction_fr ||
            "Ne prenez pas de double dose. Attendez la prochaine prise."
        ),
        prescription_expiry_date: formatDateYMD_(row.prescription_expiry_date),
        stock_empty_date: formatDateYMD_(row.stock_empty_date),
        prescription_status: String(row.prescription_status || "ACTIVE"),
        active: row.active !== false && String(row.active).toLowerCase() !== "false",
        updated_at: new Date().toISOString()
      };
      firestorePatchDocument_("medications/" + docData.id, docData);
    });
  }

  if (examSheet) {
    const examRows = getSheetObjects_(examSheet);
    examRows.forEach(row => {
      if (!row.id) return;
      const examData = {
        id: String(row.id),
        name_fr: String(row.name_fr || ""),
        name_he: String(row.name_he || ""),
        state: String(row.state || "NEEDS_REFERRAL"),
        last_exam_date: formatDateYMD_(row.last_exam_date),
        next_target_date: formatDateYMD_(row.next_target_date),
        state_updated_at: formatDateYMD_(row.state_updated_at || new Date())
      };
      firestorePatchDocument_("medical_exams/" + examData.id, examData);
    });
  }

  // Notify mother's phone to refresh AlarmManager
  sendFcmTopicMessage_("patient_schedule_sync", {
    type: "SYNC_SCHEDULE",
    updated_at: new Date().toISOString()
  });
}

/**
 * 2. The 5-Minute Watchdog (Configure as a Time-Driven Trigger every 5 minutes)
 * Checks today's expected medication times against Firestore `call_logs`:
 *   - Silent Failure Detection: > 10 minutes past due AND `ringed_at == null`
 *   - Unanswered Call Detection: > 35 minutes past due AND `completed_at == null`
 */
function runWatchdogCheck() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const medSheet = ss.getSheetByName("Medications");
  if (!medSheet) return;

  const config = getConfigMap_(ss.getSheetByName("Config_And_Contacts"));
  const motherPhone = config.mother_phone || "+972500000000";
  const caregiverEmails = (config.caregiver_emails || "")
    .split(",")
    .map(e => e.trim())
    .filter(Boolean);

  const now = new Date();
  const todayStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "yyyy-MM-dd");
  const nowMinutes = now.getHours() * 60 + now.getMinutes();

  const medRows = getSheetObjects_(medSheet);
  medRows.forEach(med => {
    if (!med.id || String(med.active).toLowerCase() === "false") return;
    const times = String(med.scheduled_times || "08:00")
      .split(",")
      .map(t => t.trim())
      .filter(Boolean);

    times.forEach(timeHHmm => {
      const parts = timeHHmm.split(":");
      const schedH = parseInt(parts[0], 10);
      const schedM = parseInt(parts[1], 10);
      const schedTotalMin = schedH * 60 + schedM;
      const elapsedMin = nowMinutes - schedTotalMin;

      if (elapsedMin < SILENT_FAILURE_MINUTES || elapsedMin > 180) return;

      const docId = todayStr + "_" + timeHHmm.replace(":", "-") + "_" + med.id;
      const logDoc = firestoreGetDocument_("call_logs/" + docId);

      const ringedAt = logDoc ? logDoc.ringed_at : null;
      const completedAt = logDoc ? logDoc.completed_at : null;
      const alreadyWatchdogAlerted =
        logDoc &&
        (logDoc.outcome === "WATCHDOG_SILENT_FAILURE" ||
          logDoc.watchdog_alert_sent === "true");

      if (alreadyWatchdogAlerted) return;

      // Case A: Silent Failure (> 10 mins and phone never reported RINGED)
      if (elapsedMin >= SILENT_FAILURE_MINUTES && !ringedAt) {
        const subject = `⚠️ ALERTE SILENCIEUSE : Le téléphone de Maman n'a pas sonné (${timeHHmm} - ${med.name_fr})`;
        const body =
          `⚠️ ALERTE : Le téléphone de Maman n'a pas sonné pour le médicament de ${timeHHmm} (${med.name_fr}).\n\n` +
          `Il est peut-être éteint, déchargé, hors ligne, ou bloqué par Xiaomi HyperOS.\n\n` +
          `👉 Cliquez ici pour l'appeler immédiatement : tel:${motherPhone}`;

        triggerCaregiverWatchdogAlert_(docId, med.id, subject, body, caregiverEmails, "WATCHDOG_SILENT_FAILURE");
      }
      // Case B: Unanswered / Uncompleted after 35 minutes
      else if (elapsedMin >= UNANSWERED_FAILURE_MINUTES && !completedAt) {
        const subject = `⚠️ ALERTE NON CONFIRMÉE : Médicament de ${timeHHmm} (${med.name_fr}) sans réponse`;
        const body =
          `⚠️ ALERTE : Le rappel de ${timeHHmm} (${med.name_fr}) n'a pas été confirmé après 35 minutes.\n\n` +
          `👉 Cliquez ici pour appeler Maman : tel:${motherPhone}`;

        triggerCaregiverWatchdogAlert_(docId, med.id, subject, body, caregiverEmails, "NO_ANSWER_3_TRIES");
      }
    });
  });
}

/**
 * 3. Weekly Summary Email (Configure as a Time-Driven Trigger every Sunday at 20:00)
 */
function sendWeeklySummary() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const config = getConfigMap_(ss.getSheetByName("Config_And_Contacts"));
  const caregiverEmails = (config.caregiver_emails || "")
    .split(",")
    .map(e => e.trim())
    .filter(Boolean);
  if (caregiverEmails.length === 0) return;

  const logs = firestoreListDocuments_("call_logs", 100);
  const oneWeekAgo = Date.now() - 7 * 24 * 3600 * 1000;
  const recent = logs.filter(l => Number(l.scheduled_timestamp || 0) >= oneWeekAgo);

  let takenCount = 0;
  let forgotCount = 0;
  let rowsHtml = "";

  recent.forEach(l => {
    if (l.outcome === "TAKEN") takenCount++;
    if (l.outcome === "FORGOT") forgotCount++;
    rowsHtml += `<tr>
      <td style="padding:6px;border:1px solid #ccc;">${l.id || ""}</td>
      <td style="padding:6px;border:1px solid #ccc;">${l.status || ""}</td>
      <td style="padding:6px;border:1px solid #ccc;"><b>${l.outcome || "PENDING"}</b></td>
      <td style="padding:6px;border:1px solid #ccc;">${l.acknowledged_by || "-"}</td>
    </tr>`;
  });

  const htmlBody = `
    <h2>🌸 Résumé Hebdomadaire des Médicaments de Maman / סיכום שבועי</h2>
    <p><b>Prises confirmées (TAKEN) :</b> ${takenCount} | <b>Oublis signalés (FORGOT) :</b> ${forgotCount}</p>
    <table style="border-collapse:collapse;width:100%;font-family:sans-serif;">
      <thead>
        <tr style="background:#15803D;color:#fff;">
          <th style="padding:8px;border:1px solid #ccc;">Date & Heure</th>
          <th style="padding:8px;border:1px solid #ccc;">Statut</th>
          <th style="padding:8px;border:1px solid #ccc;">Résultat</th>
          <th style="padding:8px;border:1px solid #ccc;">Pris en charge par</th>
        </tr>
      </thead>
      <tbody>${rowsHtml}</tbody>
    </table>
  `;

  MailApp.sendEmail({
    to: caregiverEmails.join(","),
    subject: "🌸 Résumé Hebdomadaire Médicaments Maman",
    htmlBody: htmlBody
  });
}

/* ==================== Internal Firestore & FCM Helpers ==================== */

function triggerCaregiverWatchdogAlert_(docId, medId, subject, body, emails, outcome) {
  firestorePatchDocument_("call_logs/" + docId, {
    id: docId,
    med_id: medId,
    outcome: outcome,
    alert_level: "IMMEDIATE",
    watchdog_alert_sent: "true"
  });

  sendFcmTopicMessage_("caregiver_alerts", {
    type: "CAREGIVER_ALERT",
    call_log_id: docId,
    title: subject,
    body: body,
    alert_level: "IMMEDIATE"
  });

  if (emails.length > 0) {
    MailApp.sendEmail({
      to: emails.join(","),
      subject: subject,
      body: body
    });
  }
}

function getOAuthToken_() {
  const props = PropertiesService.getScriptProperties();
  const clientEmail = props.getProperty("FIREBASE_CLIENT_EMAIL");
  const privateKey = (props.getProperty("FIREBASE_PRIVATE_KEY") || "").replace(/\\n/g, "\n");
  if (!clientEmail || !privateKey) {
    throw new Error("Missing FIREBASE_CLIENT_EMAIL or FIREBASE_PRIVATE_KEY in Script Properties.");
  }

  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  };

  const toSign =
    Utilities.base64EncodeWebSafe(JSON.stringify(header)) +
    "." +
    Utilities.base64EncodeWebSafe(JSON.stringify(claim));
  const signatureBytes = Utilities.computeRsaSha256Signature(toSign, privateKey);
  const jwt = toSign + "." + Utilities.base64EncodeWebSafe(signatureBytes);

  const response = UrlFetchApp.fetch("https://oauth2.googleapis.com/token", {
    method: "post",
    payload: {
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt
    }
  });
  return JSON.parse(response.getContentText()).access_token;
}

function firestorePatchDocument_(docPath, plainObj) {
  const projectId = PropertiesService.getScriptProperties().getProperty("FIREBASE_PROJECT_ID");
  const token = getOAuthToken_();
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${docPath}`;

  const fields = {};
  Object.keys(plainObj).forEach(k => {
    fields[k] = toFirestoreValue_(plainObj[k]);
  });

  UrlFetchApp.fetch(url, {
    method: "patch",
    contentType: "application/json",
    headers: { Authorization: "Bearer " + token },
    payload: JSON.stringify({ fields: fields }),
    muteHttpExceptions: true
  });
}

function firestoreGetDocument_(docPath) {
  const projectId = PropertiesService.getScriptProperties().getProperty("FIREBASE_PROJECT_ID");
  const token = getOAuthToken_();
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${docPath}`;
  const res = UrlFetchApp.fetch(url, {
    method: "get",
    headers: { Authorization: "Bearer " + token },
    muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) return null;
  return fromFirestoreDocument_(JSON.parse(res.getContentText()));
}

function firestoreListDocuments_(collectionName, pageSize) {
  const projectId = PropertiesService.getScriptProperties().getProperty("FIREBASE_PROJECT_ID");
  const token = getOAuthToken_();
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collectionName}?pageSize=${pageSize || 50}`;
  const res = UrlFetchApp.fetch(url, {
    method: "get",
    headers: { Authorization: "Bearer " + token },
    muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) return [];
  const data = JSON.parse(res.getContentText());
  return (data.documents || []).map(fromFirestoreDocument_);
}

function sendFcmTopicMessage_(topic, dataMap) {
  const projectId = PropertiesService.getScriptProperties().getProperty("FIREBASE_PROJECT_ID");
  const token = getOAuthToken_();
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const stringData = {};
  Object.keys(dataMap).forEach(k => {
    stringData[k] = String(dataMap[k]);
  });

  UrlFetchApp.fetch(url, {
    method: "post",
    contentType: "application/json",
    headers: { Authorization: "Bearer " + token },
    payload: JSON.stringify({
      message: {
        topic: topic,
        data: stringData,
        android: { priority: "HIGH" }
      }
    }),
    muteHttpExceptions: true
  });
}

function toFirestoreValue_(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "boolean") return { booleanValue: val };
  if (typeof val === "number") return Number.isInteger(val) ? { integerValue: String(val) } : { doubleValue: val };
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue_) } };
  return { stringValue: String(val) };
}

function fromFirestoreDocument_(doc) {
  const out = {};
  const fields = (doc && doc.fields) || {};
  Object.keys(fields).forEach(k => {
    const f = fields[k];
    if ("stringValue" in f) out[k] = f.stringValue;
    else if ("integerValue" in f) out[k] = Number(f.integerValue);
    else if ("doubleValue" in f) out[k] = Number(f.doubleValue);
    else if ("booleanValue" in f) out[k] = Boolean(f.booleanValue);
    else out[k] = null;
  });
  return out;
}

function getSheetObjects_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];
  const headers = values[0].map(h => String(h).trim());
  return values.slice(1).map(row => {
    const obj = {};
    headers.forEach((h, i) => {
      obj[h] = row[i];
    });
    return obj;
  });
}

function getConfigMap_(sheet) {
  if (!sheet) return {};
  const values = sheet.getDataRange().getValues();
  const map = {};
  values.forEach(row => {
    if (row[0]) map[String(row[0]).trim()] = String(row[1] || "").trim();
  });
  return map;
}

function formatDateYMD_(val) {
  if (!val) return "";
  if (val instanceof Date) {
    return Utilities.formatDate(val, Session.getScriptTimeZone(), "yyyy-MM-dd");
  }
  return String(val).trim();
}
