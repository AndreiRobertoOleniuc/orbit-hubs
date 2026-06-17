import { useDispatch, useSelector } from "react-redux"

import type { AppDispatch, RootState } from "@/lib/store"

// Typed versions of the React-Redux hooks. Feature cards must use these,
// not the raw `useDispatch` / `useSelector`.
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()
