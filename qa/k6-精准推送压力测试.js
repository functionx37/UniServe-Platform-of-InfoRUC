import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '30s', target: 100 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.05'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const token = __ENV.TOKEN || 'replace-with-real-token'

export default function () {
  const payload = JSON.stringify({
    title: '压测通知',
    content: '这是用于验证 F-10/F-11 推送接口稳定性的压测消息。',
    grade: '2022',
    major: '计算机科学与技术',
    identity: '全部',
    channels: ['站内消息'],
  })

  const params = {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  }

  const response = http.post(`${baseUrl}/admin/push/send`, payload, params)

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response contains success field': (res) => res.body.includes('success'),
  })

  sleep(1)
}
