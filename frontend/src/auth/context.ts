import { createContext } from 'react'
import type { RegistrationInput, UserProfile } from '../services/apiClient'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: UserProfile | null
  signIn: (email: string, password: string) => Promise<void>
  signInWithGoogle: () => Promise<void>
  signUp: (input: RegistrationInput) => Promise<UserProfile>
  signOut: () => Promise<void>
}

export const authContext = createContext<AuthContextValue | null>(null)
