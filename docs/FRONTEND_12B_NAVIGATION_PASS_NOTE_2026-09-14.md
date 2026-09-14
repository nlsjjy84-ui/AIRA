# 12B Frontend structure and context — PASS

- `/explore` is the focused canonical exploration path, linked from MAIN and header search. Search identifies COMPANY and SECURITY by backend canonical ID/type/key; the Ask step offers perspectives, not a free question field.
- The path presents MAIN → Ask → Inspect → Relate → Assess as progressive steps. Inspect divides category from detail. Browser history snapshots the selected target, type, perspective, category, exact period, receipt, predicate, comparison, event, Assessment, and Evidence context; Back restores the previous snapshot.
- Changing canonical target resets period, receipt, comparison, and event context. SECURITY never becomes COMPANY automatically. Assess requires a selected Event and reads the backend supersession terminal; an absent Assessment stays NO_DATA.
- Exact financial reads send user-selected period and receipt unchanged. KRX Current uses the backend's official D response. No frontend latest, eligibility, or stale threshold calculation was added.
- Loading and empty/NO_DATA/PARTIAL/CONFLICTING/BLOCKED/UNSUPPORTED/UNAVAILABLE have distinct presentation; STALE has text support only for a backend-issued state, without local inference.
- Existing private account, Interest, Briefing, and Alert routes remain in the established MAIN screen. No migration changed and no 12C visualization was added.
- Targeted navigation/API tests plus existing App tests: 41 passed. Vite production build passed.
