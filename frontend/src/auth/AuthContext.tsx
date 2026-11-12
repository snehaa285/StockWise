import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { authContext, type AuthContextValue, type AuthStatus } from './context'
import {
  clearAccessToken,
  loginUser,
  loginWithFirebase,
  logoutUser,
  registerUser,
  restoreSession,
  type RegistrationInput,
  type UserProfile,
} from '../services/apiClient'
import { signInWithGoogle as firebaseGoogleSignIn } from '../services/firebase'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<UserProfile | null>(null)

  useEffect(() => {
    let active = true
    restoreSession()
      .then((profile) => {
        if (active) {
          setUser(profile)
          setStatus(profile ? 'authenticated' : 'unauthenticated')
        }
      })
      .catch(() => {
        clearAccessToken()
        if (active) {
          setUser(null)
          setStatus('unauthenticated')
        }
      })

    return () => {
      active = false
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const profile = await loginUser(email, password)
    setUser(profile)
    setStatus('authenticated')
  }, [])

  const signInWithGoogle = useCallback(async () => {
    const idToken = await firebaseGoogleSignIn()
    const profile = await loginWithFirebase(idToken)
    setUser(profile)
    setStatus('authenticated')
  }, [])

  const signUp = useCallback((input: RegistrationInput) => registerUser(input), [])

  const signOut = useCallback(async () => {
    await logoutUser()
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const value: AuthContextValue = useMemo(
    () => ({ status, user, signIn, signInWithGoogle, signUp, signOut }),
    [status, user, signIn, signInWithGoogle, signUp, signOut],
  )

  return <authContext.Provider value={value}>{children}</authContext.Provider>
}
