import { createSlice, type PayloadAction } from "@reduxjs/toolkit"

import { SCOPE_ALL } from "@/features/search/scopeUtils"
import type { SearchFormState } from "@/features/search/SearchForm"
import type { SearchResponseDto } from "@/types/api"

/**
 * Persists the demand-side search across navigation: leaving /search and coming
 * back restores the last query, filters, and results instead of an empty form.
 * The network call still runs through `useSearchMutation`; this slice is only
 * the in-session memory of what was last searched.
 */
export interface SearchState {
  form: SearchFormState
  submitted: boolean
  results: SearchResponseDto | null
  history: SearchHistoryEntry[]
}

export interface SearchHistoryEntry {
  id: string
  form: SearchFormState
  results: SearchResponseDto
  searchedAt: string
}

export const SEARCH_HISTORY_STORAGE_KEY = "orbit.search-history.v1"
const MAX_SEARCH_HISTORY = 3

function loadSearchHistory(): SearchHistoryEntry[] {
  if (typeof window === "undefined") return []
  try {
    const stored = JSON.parse(
      window.localStorage.getItem(SEARCH_HISTORY_STORAGE_KEY) ?? "[]"
    ) as unknown
    if (!Array.isArray(stored)) return []
    return stored
      .filter(
        (entry): entry is SearchHistoryEntry =>
          typeof entry === "object" &&
          entry !== null &&
          typeof (entry as SearchHistoryEntry).id === "string" &&
          typeof (entry as SearchHistoryEntry).searchedAt === "string" &&
          typeof (entry as SearchHistoryEntry).form?.query === "string" &&
          Array.isArray((entry as SearchHistoryEntry).results?.results)
      )
      .slice(0, MAX_SEARCH_HISTORY)
  } catch {
    return []
  }
}

const initialState: SearchState = {
  form: {
    query: "",
    scope: SCOPE_ALL,
    wantFrom: "",
    wantTo: "",
    wantPensum: "",
  },
  submitted: false,
  results: null,
  history: loadSearchHistory(),
}

const searchSlice = createSlice({
  name: "search",
  initialState,
  reducers: {
    patchSearchForm(state, action: PayloadAction<Partial<SearchFormState>>) {
      state.form = { ...state.form, ...action.payload }
    },
    setSearchSubmitted(state, action: PayloadAction<boolean>) {
      state.submitted = action.payload
    },
    setSearchResults(state, action: PayloadAction<SearchResponseDto | null>) {
      state.results = action.payload
    },
    saveCompletedSearch(
      state,
      action: PayloadAction<{
        form: SearchFormState
        results: SearchResponseDto
        searchedAt: string
      }>
    ) {
      const { form, results, searchedAt } = action.payload
      const signature = JSON.stringify(form)
      state.history = [
        { id: searchedAt, form, results, searchedAt },
        ...state.history.filter(
          (entry) => JSON.stringify(entry.form) !== signature
        ),
      ].slice(0, MAX_SEARCH_HISTORY)
    },
    restoreSearch(state, action: PayloadAction<string>) {
      const entry = state.history.find((item) => item.id === action.payload)
      if (!entry) return
      state.form = entry.form
      state.results = entry.results
      state.submitted = true
    },
    removeSearchHistory(state, action: PayloadAction<string>) {
      state.history = state.history.filter((item) => item.id !== action.payload)
    },
    resetSearch() {
      return { ...initialState, history: loadSearchHistory() }
    },
  },
})

export const {
  patchSearchForm,
  setSearchSubmitted,
  setSearchResults,
  saveCompletedSearch,
  restoreSearch,
  removeSearchHistory,
  resetSearch,
} = searchSlice.actions
export default searchSlice.reducer
