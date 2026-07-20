import { useNavigate } from "react-router-dom"

import type { CreateRequestPrefill } from "@/features/requests/CreateRequestView"
import { SearchForm, type SearchFormState } from "@/features/search/SearchForm"
import { SearchResults } from "@/features/search/SearchResults"
import {
  patchSearchForm,
  setSearchResults,
  setSearchSubmitted,
} from "@/features/search/searchSlice"
import { decodeScope } from "@/features/search/scopeUtils"
import {
  useGetHubsQuery,
  useGetRegionsQuery,
  useSearchMutation,
} from "@/lib/api"
import { useAppDispatch, useAppSelector } from "@/lib/hooks"
import type { SearchRequestBody } from "@/types/api"

/**
 * The demand-side discovery surface — one page that toggles between the search
 * form and the results. Scope is strict; widening is user-driven (reference
 * §5.4): the widen control re-runs the same query one scope level broader.
 */
export function SearchView() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  // Search state lives in Redux so leaving /search and returning restores the
  // last query, filters, and results (persisted across navigation).
  const form = useAppSelector((s) => s.search.form)
  const submitted = useAppSelector((s) => s.search.submitted)
  const persistedResults = useAppSelector((s) => s.search.results)
  const [runSearch, { data, isLoading, isError }] = useSearchMutation()
  const { data: regions = [] } = useGetRegionsQuery()
  const { data: hubs = [] } = useGetHubsQuery()

  // Prefer this session's live mutation result; fall back to the persisted one
  // when we've navigated back to an earlier search.
  const results = data ?? persistedResults ?? undefined

  const patch = (p: Partial<SearchFormState>) => dispatch(patchSearchForm(p))

  const runWith = async (scopeValue: string) => {
    if (!form.query.trim()) return
    const body: SearchRequestBody = {
      query: form.query.trim(),
      scope: decodeScope(scopeValue),
      wantFrom: form.wantFrom || null,
      wantTo: form.wantTo || null,
      wantPensumPct: form.wantPensum ? Number(form.wantPensum) : null,
    }
    dispatch(setSearchSubmitted(true))
    try {
      const res = await runSearch(body).unwrap()
      dispatch(setSearchResults(res))
    } catch {
      // isError surfaces the failure in SearchResults.
    }
  }

  const onWiden = (scopeValue: string) => {
    patch({ scope: scopeValue })
    void runWith(scopeValue)
  }

  // No match → carry the whole search over into the "Anfrage ausschreiben"
  // form, so nothing has to be typed twice.
  const onCreateRequest = () => {
    const prefill: CreateRequestPrefill = {
      queryText: form.query.trim(),
      scope: form.scope,
      wantFrom: form.wantFrom,
      wantTo: form.wantTo,
      wantPensum: form.wantPensum,
    }
    navigate("/requests/new", { state: prefill })
  }

  if (submitted) {
    return (
      <SearchResults
        query={form.query}
        data={results}
        isLoading={isLoading}
        isError={isError}
        scope={form.scope}
        regions={regions}
        hubs={hubs}
        onEdit={() => dispatch(setSearchSubmitted(false))}
        onWiden={onWiden}
        onCreateRequest={onCreateRequest}
      />
    )
  }

  return (
    <SearchForm
      {...form}
      onChange={patch}
      onSearch={() => void runWith(form.scope)}
    />
  )
}
