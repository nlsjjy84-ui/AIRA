import { useState } from 'react'
import { saveStudyNote } from './api/stockApi.js'

/** 설명을 「내 공부」 노트로 저장하는 버튼. 저장한 설명은 /study에서 회사·주제별로 다시 볼 수 있다. */
export default function SaveToStudy({ note }) {
  const [state, setState] = useState('idle')
  if (!note?.body) return null
  async function save() {
    setState('saving')
    try { await saveStudyNote(note); setState('saved') }
    catch (error) { setState(error.status === 423 ? 'demo' : error.status === 401 ? 'login' : 'error') }
  }
  const label = { idle: '📚 공부 노트에 저장', saving: '저장하는 중…', saved: '📚 저장했어요 · 내 공부에서 보기', demo: '데모 계정은 저장할 수 없어요', login: '로그인하면 저장할 수 있어요', error: '저장하지 못했어요 · 다시 누르기' }[state]
  if (state === 'saved') return <a className="study-save saved" href="/study">{label}</a>
  return <button type="button" className="study-save" onClick={save} disabled={state === 'saving' || state === 'demo'}>{label}</button>
}
