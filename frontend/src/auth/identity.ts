import { createContext, useContext } from 'react'
export type Organization = { id: string; name: string; role: 'owner' | 'admin' | 'member' }
export type Identity = { id: string; email: string; organization: Organization | null }
export const IdentityContext = createContext<Identity | null>(null)
export function useIdentity() { return useContext(IdentityContext)! }
