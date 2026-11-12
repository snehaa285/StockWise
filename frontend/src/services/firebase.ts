import { initializeApp, getApps, type FirebaseApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider, signInWithPopup, type Auth } from 'firebase/auth'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '',
}

export function isFirebaseConfigured(): boolean {
  return Boolean(
    firebaseConfig.apiKey &&
    firebaseConfig.authDomain &&
    firebaseConfig.projectId &&
    firebaseConfig.appId,
  )
}

function getOrInitApp(): FirebaseApp {
  if (getApps().length > 0) {
    return getApps()[0]
  }
  return initializeApp(firebaseConfig)
}

export async function signInWithGoogle(): Promise<string> {
  if (!isFirebaseConfigured()) {
    throw new Error(
      'Google sign-in is not configured. Add VITE_FIREBASE_* environment variables to configure Firebase.',
    )
  }
  const app = getOrInitApp()
  const auth: Auth = getAuth(app)
  const provider = new GoogleAuthProvider()
  provider.setCustomParameters({ prompt: 'select_account' })

  const credential = await signInWithPopup(auth, provider)
  const idToken = await credential.user.getIdToken()
  return idToken
}

