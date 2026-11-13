import http from 'k6/http';
import { check } from 'k6';

const rate = Number(__ENV.RATE || 5);
const duration = __ENV.DURATION || '2m';
const preAllocatedVUs = Number(__ENV.PREALLOCATED_VUS || 10);

export const options = {
  scenarios: {
    submit: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs: Math.max(preAllocatedVUs, Number(__ENV.MAX_VUS || 50)),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const apiKey = __ENV.QUEUEFORGE_API_KEY;
  if (!apiKey) throw new Error('Set QUEUEFORGE_API_KEY');
  const unique = `${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${__ENV.BASE_URL || 'http://127.0.0.1:8080'}/api/v1/jobs`,
    JSON.stringify({ jobType: 'TEST_JOB', payload: `load-${unique}`, priority: 5 }),
    { headers: { 'Content-Type': 'application/json', 'X-API-Key': apiKey, 'Idempotency-Key': `load-${unique}` } },
  );
  check(response, { 'job accepted': (r) => r.status === 201 || r.status === 200 });
}
