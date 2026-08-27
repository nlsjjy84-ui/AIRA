import { request } from './http.js'

export const getOfficialEvidence = (evidenceId, signal) => request(`/api/evidence/${evidenceId}`, { signal })
