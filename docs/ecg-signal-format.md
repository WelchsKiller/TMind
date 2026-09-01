# ECG signal 파트 형식 (합의 완료)

`POST /api/member/session/{sessionId}/hrv` 의 `signal` 파트 규격입니다.

공개키 API 배포 전에는 **평문 CSV**, 배포 후에는 **암호화 JSON** 으로 앱이 자동 전환합니다.
앱 재배포 없이 전환되므로, 공개키 API 배포 후 별도 조치는 필요하지 않습니다.

---

## 1. 자동 전환 동작

앱은 HRV 업로드 직전에 `GET /api/member/crypto/public-key` 를 호출합니다.

- **공개키 수신 성공** → `keyId` 와 공개키를 저장하고, 이후 업로드는 암호화 JSON (2번)
- **실패(현재 500 포함)** → 평문 CSV (3번) 로 업로드하고, 6시간 뒤 재시도

즉 공개키 API 를 배포하시면 늦어도 6시간 안에 모든 단말이 암호화 전송으로 넘어갑니다.
암호화 도중 예외가 발생해도 평문 CSV 로 폴백해 측정 데이터가 유실되지 않도록 했습니다.

### 공개키 API 응답 형식

아래 형태로 내려주시면 그대로 사용합니다. `publicKey` 는 PEM 또는 Base64 X.509
(SubjectPublicKeyInfo) 둘 다 받으며, RSA 3072bit 를 기준으로 구현했습니다.

```json
{
  "success": true,
  "data": {
    "keyId": "ecg-2026-09",
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBojANBgkq...\n-----END PUBLIC KEY-----"
  }
}
```

---

## 2. 암호화 전송 (공개키 API 배포 후)

| 항목 | 값 |
|---|---|
| 파트 이름 | `signal` |
| 파일명 | `ecg_{measuredAt}.json` (예: `ecg_1756701234567.json`) |
| Content-Type | `application/json` |
| 본문 | 아래 JSON 1건 |

`{measuredAt}` 은 `data` 파트의 `measuredAt` 과 동일한 epoch milliseconds 입니다.
`keyId` 는 요청 헤더나 `data` 파트가 아니라 **이 JSON 안에만** 담습니다.

### 알고리즘

| 구분 | 방식 |
|---|---|
| 본문 암호화 | AES-256-GCM (IV 12 byte, Auth Tag 16 byte, tag length 128 bit) |
| CEK 래핑 | RSA-OAEP-256 (SHA-256 / MGF1-SHA-256) |
| 인코딩 | Base64URL, padding 없음 |
| 평문 | 3번의 CSV 원문 (UTF-8) |

### JSON 필드

| 필드 | 설명 | 5분 측정 기준 길이 |
|---|---|---|
| `keyId` | 공개키 API 가 내려준 값을 그대로 회신 | 서버 지정 |
| `encryptedCek` | RSA-OAEP-256 으로 래핑한 AES-256 키 (384 byte) | 512 chars |
| `iv` | AES-GCM 초기화 벡터 (12 byte) | 16 chars |
| `ciphertext` | AES-GCM 암호문 (Auth Tag 제외) | 약 1,766,554 chars |
| `authTag` | AES-GCM 인증 태그 (16 byte) | 22 chars |

### 실제 출력 샘플 1건

아래는 3번 CSV 의 앞 8샘플분을 실제로 암호화해 얻은 출력입니다.
(`keyId` 는 예시값, 공개키는 검증용으로 생성한 RSA-3072 키입니다.)

```json
{
  "keyId": "ecg-2026-09",
  "encryptedCek": "AkAjJP6gCT71-AO6qtUu5VOFtQz67F0ZBKjsPNljg1rQetq6JG2vot1DxcNj8uMIEHmilrJwLiNdX1DuFn8kFhsXec9zRSxerYgvJryPxzLVI7Jqz18KHu8VF9-Fj2wyCjJGM-9dVkg-9ree0ZEoFIMRgRrRAWHm0yhplGeDuOxl_mHT00maZnzm0nd0Bv1XtI0jz4zccKmnYf3slJhWzL5xWF5iwtDjqL6CbFNJFuoS4H--RjfJ32qCk92XaC31Dy3FJpjorzCtNocaqXjWBT129UUPD44QrxEc0CJo6ICFNkDP6fltyzs2vHD0vV6UJ1kulUsDnSLCMxiQg6wWG0xE2gsy2wpdsVuqFiJjXFt4TZLRM04b0ZjSMlkpKsU1uZDFP_Zzx4DVAKZG9OTX3b1L6VoRle0qVQ7ZxFGNgp6eOhJGiKEf56ZAKRifJDTerOuUIivuZJtpvSDuSqVmegsKpVPZFoGA9qX14ga94MBOh5mY-Bwt35oNrMGkuYed",
  "iv": "gspTfLH962psKhAE",
  "ciphertext": "Ycls2yhGFIckM5r1JiENO4aArRx81ecmLGPLcK2Ow2PkbErl602oTrmn3qkhlupG6UiMWZpCmeGyT2P5AHq_l65Nj-VvG6kF6yisWc5M-cyz2p1hsvyWQOKwaG6ZvoD6OQjH8oX9MpqOe9PO0KkjAn_odvPM9auCm7kO5_55dSg",
  "authTag": "SFP3TTDPSsUl8om4si8iyg"
}
```

5분 측정 전체를 암호화하면 JSON 이 약 1,767,182 bytes (1.7 MB) 가 됩니다.
평문 CSV(1.3 MB) 대비 Base64 때문에 약 33% 커지므로, 업로드 본문 크기 제한이 있다면
2 MB 이상으로 잡아 주세요.

### 복호화 절차

1. `encryptedCek` 을 Base64URL 디코딩 후 서버 개인키로 RSA-OAEP-256 복호화 → AES-256 키
2. `ciphertext` 와 `authTag` 를 각각 Base64URL 디코딩 후 순서대로 이어 붙임
3. `iv` 와 위 AES 키로 AES-256-GCM 복호화 (tag length 128 bit)
4. 결과를 UTF-8 로 디코딩하면 3번의 CSV 원문

---

## 3. 평문 CSV 전송 (공개키 API 배포 전 폴백)

| 항목 | 값 |
|---|---|
| 파트 이름 | `signal` |
| 파일명 | `ecg-raw-signal.csv` |
| Content-Type | `text/csv` |
| 인코딩 | UTF-8 |
| 샘플링 레이트 | 250 Hz (`data` 파트의 `fs` 와 동일) |
| 5분 측정 기준 | 75,000 행 (헤더 1행 별도), 약 1,324,915 bytes |

```csv
index,value
0,-0.043361254
1,-0.043960024
2,-0.065118484
3,-0.06671143
4,-0.037129056
5,-0.03264137
6,-0.03842847
7,-0.04958368
```

`index` 는 0 부터 시작하는 샘플 순번, `value` 는 mV 단위 진폭입니다.
`index / fs` 로 측정 시작 시점 기준 경과 초를 계산할 수 있습니다.
암호화 전환 후에도 복호화 결과는 동일한 CSV 이므로 파싱 로직은 재사용 가능합니다.
