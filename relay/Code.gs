/**
 * KLAS Watch -> Gmail relay
 * 1) RECIPIENT를 본인 Gmail 주소로 바꾸세요.
 * 2) TOKEN을 길고 랜덤한 값으로 바꾸세요.
 * 3) Apps Script에서 '웹 앱'으로 배포: 실행 사용자=나, 액세스=모든 사용자.
 * 4) 배포 URL과 TOKEN을 Android 앱에 입력하세요.
 */
const RECIPIENT = 'YOUR_GMAIL@gmail.com';
const TOKEN = 'CHANGE_THIS_TO_A_LONG_RANDOM_TOKEN';

function doGet() {
  return json_({ok: true, service: 'KLAS Watch Relay'});
}

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents || '{}');
    if (!data.token || data.token !== TOKEN) {
      return json_({ok: false, error: 'unauthorized'});
    }

    const category = clean_(data.category || '공지', 30);
    const target = clean_(data.target || 'KLAS', 80);
    const title = clean_(data.title || '새 공지', 180);
    const subject = `[KLAS][${category}][${target}] ${title}`.slice(0, 240);

    const body = [
      'SOURCE: KLAS Watch',
      `CATEGORY: ${category}`,
      `TARGET: ${target}`,
      `TITLE: ${title}`,
      `DATE_TEXT: ${clean_(data.dateText || '', 100)}`,
      `URL: ${String(data.url || '')}`,
      '',
      'CONTENT:',
      String(data.detail || '').slice(0, 12000),
      '',
      `DETECTED_AT_MS: ${String(data.detectedAt || '')}`
    ].join('\n');

    MailApp.sendEmail(RECIPIENT, subject, body, {name: 'KLAS Watch'});
    return json_({ok: true});
  } catch (err) {
    return json_({ok: false, error: String(err)});
  }
}

function clean_(value, maxLen) {
  return String(value).replace(/[\r\n\t]+/g, ' ').trim().slice(0, maxLen);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
