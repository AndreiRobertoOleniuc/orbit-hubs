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
    resetSearch() {
      return initialState
    },
  },
})

export const {
  patchSearchForm,
  setSearchSubmitted,
  setSearchResults,
  resetSearch,
} = searchSlice.actions
export default searchSlice.reducer
