/**
 * The English catalogue, and the source of truth for the shape of every other locale.
 *
 * Keys are grouped by the screen they appear on, with `errors` shared because the same
 * failure can surface from more than one form.
 */
export const en = {
  app: {
    // A proper noun: present in the catalogue so a locale could transliterate it, but
    // not expected to change.
    name: 'Aurevanta',
    loading: 'Loading…',
    signOut: 'Sign out'
  },
  // Shared: a role is shown wherever an organisation is named, not on one screen.
  roles: {
    OWNER: 'Owner',
    MEMBER: 'Member'
  },
  landing: {
    nav: {
      label: 'Account',
      signIn: 'Sign in',
      getStarted: 'Get started',
      openApp: 'Open Aurevanta'
    },
    hero: {
      title: 'Plan with ranges, not false confidence',
      lede: 'Aurevanta turns P10/P50/P90 estimates into a forecast you can defend — by simulating the plan, rather than adding up numbers that were never meant to be added.',
      createOrganisation: 'Create your organisation',
      continueAs: 'Continue as {{name}}'
    },
    principle: {
      title: 'Percentiles do not add',
      body1:
        'Ten tasks with a P90 of five days each do not make a 50-day project. That number assumes everything goes wrong at once, which is wildly pessimistic. Adding P50s makes the opposite mistake: it ignores that variance accumulates, and that the optimistic tail is bounded while the pessimistic one is not.',
      body2:
        'The only honest rollup is to fit a distribution to each estimate, sample it many times, and read the percentiles off the result. That engine is what the rest of the product is built on.'
    },
    features: {
      title: 'What that makes possible',
      date: {
        title: 'A date, not a distribution',
        body: 'Nobody asks for a probability curve; they ask what date you can commit to. Choose a confidence level and get one — and see honestly what going faster costs in certainty.'
      },
      variance: {
        title: 'Find what actually hurts',
        body: 'Rank work by how much it widens the forecast, not by how long it is. A 20-day task estimated 18–22 is nearly risk-free; a 5-day task estimated 2–30 is what wrecks the plan.'
      },
      calibration: {
        title: 'Estimates that improve',
        body: 'Record what actually happened, then measure how often reality landed inside the band. It should be 80%. Most teams score far below that, and knowing it is the first step to fixing it.'
      }
    }
  },
  auth: {
    backToHome: '← Aurevanta',
    fields: {
      organisationName: {
        label: 'Organisation name',
        hint: "Everyone you invite later shares this organisation's plans."
      },
      displayName: { label: 'Your name' },
      email: { label: 'Email' },
      password: {
        label: 'Password',
        hint: 'At least {{count}} characters.'
      }
    },
    register: {
      title: 'Create your organisation',
      lede: 'Aurevanta plans work in ranges rather than single dates. Set up an organisation and you can start estimating right away.',
      submit: 'Create organisation',
      submitting: 'Creating…',
      haveAccount: 'Already have an account? <signIn>Sign in</signIn>',
      // Shown when the address is already taken. Says nothing about whether that account
      // is confirmed — the server does not say, and it is not ours to guess — but the one
      // remedy a stranger cannot misuse is a link sent to that address and nowhere else.
      alreadyRegistered:
        'If that is you and the confirmation link never arrived, ask for another:',
      // Where signing up now ends. The account exists but cannot be used until the
      // address is confirmed, so this screen has to say that plainly rather than imply
      // something went wrong.
      checkEmail: {
        title: 'Confirm your email address',
        body: 'We have sent a link to {{email}}. Follow it to finish setting up your organisation — you cannot sign in until you do.',
        nothingYet:
          'It can take a minute to arrive. If it does not, check your spam folder.',
        // This screen is where a lost message is first noticed, so it cannot be the one
        // screen with nothing to do about it.
        needLink: 'Still nothing? <verify>Ask for a new link</verify>',
        signIn: 'Already confirmed? <signIn>Sign in</signIn>'
      }
    },
    login: {
      title: 'Sign in',
      submit: 'Sign in',
      submitting: 'Signing in…',
      noAccount: 'New here? <register>Create an organisation</register>',
      needLink:
        'Never received your confirmation link? <verify>Ask for a new one</verify>',
      forgotPassword: 'Forgotten your password? <reset>Choose a new one</reset>'
    },
    // Shared, because the same offer belongs on more than one screen: after the gate
    // refuses a sign-in, and after registering with an address that already exists.
    // Worded tighter than the standalone page's, which has room to discuss spam folders.
    resendConfirmation: {
      submit: 'Send a new link',
      submitting: 'Sending…',
      requested: 'If that address needs confirming, a new link is on its way.'
    },
    verifyEmail: {
      confirming: 'Confirming your address…',
      confirmed: {
        title: 'Address confirmed',
        body: 'That is everything — your organisation is ready to use.',
        signIn: '<signIn>Sign in</signIn> to get started.'
      },
      needLink: {
        title: 'Ask for a new confirmation link',
        body: 'Enter the address you registered with and we will send another link.',
        submit: 'Send a new link',
        submitting: 'Sending…',
        // Says the same thing whether or not the address has an account, because the
        // server does too — anything more precise would disclose who is registered.
        requested:
          'If that address needs confirming, a new link is on its way. It can take a minute to arrive, and it is worth checking your spam folder.'
      }
    },
    forgotPassword: {
      title: 'Reset your password',
      body: 'Enter the address you registered with and we will send a link for choosing a new password.',
      submit: 'Send a reset link',
      submitting: 'Sending…',
      // Says the same thing whether or not the address has an account, because the
      // server does too — anything more precise would disclose who is registered.
      requested:
        'If that address has an account, a link is on its way. It expires in an hour, so use it while it is fresh — and check your spam folder if nothing arrives.',
      signIn: 'Remembered it after all? <signIn>Sign in</signIn>'
    },
    resetPassword: {
      title: 'Choose a new password',
      body: 'Pick something you have not used anywhere else. This link works once.',
      submit: 'Save new password',
      submitting: 'Saving…',
      // A link that has expired or been used is exactly when someone needs another, so
      // no state of this page is a dead end.
      needLink: 'Link no longer working? <forgot>Ask for a new one</forgot>',
      done: {
        title: 'Password changed',
        body: 'Your address is confirmed as well, so you can sign in right away.',
        signIn: '<signIn>Sign in</signIn> with your new password.'
      },
      noToken: {
        title: 'That link is incomplete',
        body: 'A reset link carries a token, and this one arrived without it — some mail clients break long links across lines.',
        ask: '<forgot>Ask for a new link</forgot> and it will arrive in a moment.'
      }
    }
  },
  chooseOrganisation: {
    title: 'Choose an organisation',
    lede: 'You belong to more than one. Everything you plan is scoped to the one you pick, and you can switch later.',
    none: 'You do not belong to an organisation yet. Ask someone who does to invite you, and the invitation will arrive by email.'
  },
  dashboard: {
    title: 'You’re set up',
    body: '{{organisation}} is ready. Everything you plan from here belongs to this organisation and is visible only to its members.',
    next: 'Estimating comes next.'
  },
  notFound: {
    title: 'Page not found',
    lede: 'That address does not lead anywhere in Aurevanta.',
    back: 'Back to the front page'
  },
  errors: {
    // Keyed by the `code` the backend puts in its problem documents, so the wording
    // shown to the user is ours rather than whatever prose the server happened to send.
    codes: {
      email_already_registered: 'That email address is already registered.',
      organisation_name_unavailable: 'That organisation name is already taken.',
      organisation_name_unusable:
        'Organisation name must contain at least one letter or digit.',
      invalid_credentials: 'Email or password is incorrect.',
      email_not_verified:
        'Confirm your email address before signing in. Check your inbox for the link we sent when you registered.',
      invalid_token:
        'That link has expired or has already been used. Ask for a new one.',
      not_a_member: 'You do not belong to that organisation.',
      // The server says how long to wait in a Retry-After header, which nothing here
      // reads yet — so the wording stays vague rather than promising a time it does
      // not know.
      too_many_requests:
        'Too many requests just now. Wait a few minutes and try again.',
      registration_conflict:
        'That email address or organisation name was just taken.',
      validation_failed: 'Some fields need attention.'
    },
    // Keyed by the constraint the backend says a field failed, not by the field's name:
    // one entry per rule serves every form, and the bounds come from the server so no
    // number is repeated here. `invalid` is the fallback for a constraint with no wording
    // — the server sends no prose, so there is nothing else to show.
    validation: {
      not_blank: 'This cannot be empty.',
      // `not_blank` is for text somebody types; this is for a value they pick, where
      // "cannot be empty" would describe a box there is nothing to type into.
      not_null: 'Choose one of the options.',
      size: 'Use between {{min}} and {{max}} characters.',
      // A constraint that only bounds length above; `size` would say "between 0 and 200".
      max_size: 'Use no more than {{max}} characters.',
      email: 'Enter a valid email address.',
      invalid: 'Check this and try again.'
    },
    network: 'Could not reach the server. Check your connection and try again.',
    unknown: 'Something went wrong. Please try again.'
  }
} as const;
