import { useState } from "react"

import { SearchForm, type SearchFormState } from "@/features/search/SearchForm"
import { SearchResults } from "@/features/search/SearchResults"
import { decodeScope, SCOPE_ALL } from "@/features/search/scopeUtils"
import {
  useGetHubsQuery,
  useGetRegionsQuery,
  useSearchMutation,
} from "@/lib/api"
import type { SearchRequestBody } from "@/types/api"

/**
 * The demand-side discovery surface — one page that toggles between the search
 * form and the results. Scope is strict; widening is user-driven (reference
 * §5.4): the widen control re-runs the same query one scope level broader.
 */
export function SearchView() {
  const [form, setForm] = useState<SearchFormState>({
    query: "",
    scope: SCOPE_ALL,
    wantFrom: "",
    wantTo: "",
    wantPensum: "",
  })
  const [submitted, setSubmitted] = useState(false)
  const [runSearch, { data, isLoading, isError }] = useSearchMutation()
  const { data: regions = [] } = useGetRegionsQuery()
  const { data: hubs = [] } = useGetHubsQuery()

  const patch = (p: Partial<SearchFormState>) =>
    setForm((f) => ({ ...f, ...p }))

  const runWith = (scopeValue: string) => {
    if (!form.query.trim()) return
    const body: SearchRequestBody = {
      query: form.query.trim(),
      scope: decodeScope(scopeValue),
      wantFrom: form.wantFrom || null,
      wantTo: form.wantTo || null,
      wantPensumPct: form.wantPensum ? Number(form.wantPensum) : null,
    }
    setSubmitted(true)
    runSearch(body)
  }

  const onWiden = (scopeValue: string) => {
    patch({ scope: scopeValue })
    runWith(scopeValue)
  }

  if (submitted) {
    return (
      <SearchResults
        query={form.query}
        data={data}
        isLoading={isLoading}
        isError={isError}
        scope={form.scope}
        regions={regions}
        hubs={hubs}
        onEdit={() => setSubmitted(false)}
        onWiden={onWiden}
      />
    )
  }

  return (
    <SearchForm {...form} onChange={patch} onSearch={() => runWith(form.scope)} />
  )
}
