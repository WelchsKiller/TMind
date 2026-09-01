# ECG signal 파트 형식 (앱 반영 완료)

`POST /api/member/session/{sessionId}/hrv` 의 `signal` 파트 규격입니다.
공개키 API 배포 안내를 받아 앱에 모두 반영했습니다.

---

## 1. 자동 전환 동작

앱은 HRV 업로드 직전에 `GET /api/member/crypto/public-key` 를 호출합니다.

- **공개키 수신 성공** → `keyId` 와 공개키를 저장하고 암호화 JSON (2번) 으로 업로드
- **조회 실패** → 평문 CSV (3번) 로 업로드하고 6시간 뒤 재시도

공개키 조회에 실패하거나 암호화 중 예외가 발생하면 평문 CSV 로 폴백하므로 측정 데이터가
유실되지 않습니다. 전환 기간에 두 형태를 모두 받아주신다고 하여 그대로 두었습니다.

### 공개키 응답 파싱 확인

`publicKey` 는 X.509 SubjectPublicKeyInfo DER 의 **표준 Base64**, 업로드 페이로드의
`encryptedCek` / `iv` / `ciphertext` / `authTag` 는 **Base64URL** 로 인코딩이 다른 점
확인했고, 앱에서 디코더를 분리해 처리합니다. PEM 헤더가 붙어 있어도 파싱됩니다.
안내해주신 `MIIBojANBgkqhkiG9w0BAQEFAAOCAY8A...` 는 RSA 3072bit 로 확인했습니다.

RSA-3072 키로 암호화 → 복호화 왕복 테스트를 5분(75,000 샘플) 분량으로 통과했습니다.

---

## 2. 암호화 전송

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

### 에러 코드 재전송 (반영 완료)

| 코드 | 앱 동작 |
|---|---|
| `400 E00503` | ECG 신호를 다시 만들어 `signal` 을 포함해 1회 재전송 |
| `400 E00504` | 캐시한 공개키를 버리고 `GET /crypto/public-key` 재조회 → 재암호화 후 1회 재전송 |

재전송은 각 1회로 제한했습니다. 원인이 그대로면 같은 결과가 나오므로 반복하지 않고,
사용자에게 서버 메시지를 그대로 노출합니다. 거부된 요청은 서버에 저장되지 않는다고
확인해주셔서, 재전송 시 중복 저장은 고려하지 않았습니다.

---

## 3. 평문 CSV 전송 (공개키 조회 실패 시 폴백)

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

---

## 4. 오늘의 화면 응답 필드 (반영 완료)

추가해주신 세 필드를 앱에서 사용하고 있습니다.

| 필드 | 앱 사용처 |
|---|---|
| `participatedDays` | 주간 추이 버튼 노출 기준(7일 이상). `/participation` 값보다 우선 적용 |
| `missedType` | `AM` 이면 대시보드 인사 문구를 놓친 세션 안내로 교체 |
| `serverDate` | 단말 날짜와 비교. 어긋나면 시각 자동 설정 안내를 1회 노출 |

`serverDate` 를 쓰는 이유는, 앱의 하루치 미션 진행 상태가 단말 날짜를 키로 저장되기
때문입니다. 단말 시계가 틀어지면 서버 판정일과 진행 상태가 어긋나는데, 이제 그 상황을
감지해 사용자에게 안내할 수 있게 되었습니다.

참고로 이전에 문의드렸던 `studyDayIndex`(연구 N일차)는 이번 추가 항목에 없어서
앱 모델에서 제거했습니다. 필요해지면 다시 요청드리겠습니다.
