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
    signOut: 'Sign out',
    nav: {
      label: 'Sections',
      overview: 'Overview',
      projects: 'Projects',
      members: 'Members',
      settings: 'Settings'
    },
    switcher: {
      // Labels a control that shows the organisation it would switch away from, so the
      // name beside it is not itself an explanation of what the control does.
      label: 'Organisation',
      // Two organisations may share a name, and an <option> has nowhere to put a second
      // line — so where they do, the handle that tells them apart goes inline.
      named: '{{name}} ({{handle}})'
    }
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
      slug: {
        label: 'Handle',
        hint: 'Lowercase letters, numbers and hyphens. Yours to change later.',
        // A handle is an address, not a name — which is why this refusal is one somebody
        // can act on, and why the field is already holding the way past it.
        taken: 'Somebody already has that handle. We have suggested another.',
        // Two people choosing one handle in the same instant: the loser is refused by the
        // unique index, too late for the server to go and find a free one. Promising a
        // suggestion here would point at a field still holding the refused handle.
        takenWithoutSuggestion:
          'Somebody already has that handle. Choose another.'
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
    none: 'You do not belong to an organisation yet. Someone can invite you by email — or you can start one of your own.',
    // The way out of this state that does not depend on anybody else acting.
    start: {
      title: 'Start an organisation',
      body: 'You will own it, and can invite people once it exists.',
      submit: 'Create organisation',
      submitting: 'Creating…'
    }
  },
  settings: {
    title: 'Organisation',
    lede: 'What this organisation is called, and the address it answers to.',
    ownersOnly: 'Only an owner of this organisation can change these.',
    submit: 'Save changes',
    submitting: 'Saving…',
    saved: 'Saved.',
    // Said before it saves rather than after, because after is too late: nothing
    // redirects from a handle that has moved.
    handleMoves:
      'Changing the handle from {{from}} will stop any link anyone has to this organisation from working.'
  },
  projects: {
    title: 'Projects',
    lede: 'The plans {{organisation}} is working from.',
    loading: 'Loading projects…',
    none: 'No projects yet. Start one below and you can put work in it.',
    noneArchived: 'Nothing has been put away.',
    // Two states of one list rather than a filter, because the endpoint answers one or the
    // other: a row saying which it was would be a row somebody has to read to know whether
    // the work in front of them is live.
    showArchived: 'Show archived projects',
    showCurrent: 'Show current projects',
    // Said in words wherever a plan is shown, because a forecast that quietly covers less
    // than the plan is the failure this product exists to prevent — and a number nobody
    // reads is not a disclosure.
    coverage: '{{estimated}} of {{total}} items estimated',
    // The whole product, and the first screen where being wrong would not look like being
    // wrong. Everything here is hours of effort: a date needs an assumption about what a
    // working day is worth, and M4 is where somebody states one rather than this screen
    // inventing it.
    forecast: {
      title: 'Forecast',
      lede: 'Simulated from the ranges on the work above. Effort in hours, not dates.',
      loading: 'Loading forecasts…',
      none: 'No forecast yet. Answer the four questions below and ask for one — none of them has an answer this application can give for you.',
      submit: 'Forecast this plan',
      submitting: 'Simulating…',
      more: 'Advanced',
      fields: {
        capacity: {
          label: 'Things that can be under way at once',
          // Not pre-filled, and this hint is why: a box already answered is a box nobody
          // reads, and this is the number that moves the answer most.
          hint: 'Required. The same plan finishes in half the time with twice the people, so nobody can guess this for you.'
        },
        // The two hardest questions in the model, asked as percentages because nobody has
        // an opinion about a log-standard-deviation. Neither is pre-filled, for the reason
        // capacity is not: a box already answered is a box nobody reads, and answering
        // these two for somebody would be claiming their last five projects went a way
        // this application has never seen.
        teamFactor: {
          label: 'In a bad stretch, how much longer does everything take?',
          hint: 'Required, as a percentage. Answer for a stretch bad enough that only one quarter in ten is worse. Zero says nothing ever goes wrong for everybody at once.'
        },
        scopeGrowth: {
          legend: 'How much does a plan like this usually grow?',
          hint: 'Required, as percentages of the work already listed. Two numbers, because the answer is a range: a usual amount and a bad one. Zero and zero says nothing will be discovered that nobody has thought of.',
          low: 'Usually at least',
          high: 'And as much as'
        },
        sampleCount: {
          label: 'Simulated runs',
          hint: 'Ten thousand unless you say otherwise, which is accurate to well under a percent.'
        }
      },
      // P10 to P90 is eight tenths of the probability, which is what makes this sentence
      // true rather than merely reassuring.
      band: 'An 80% chance of taking between {{low}} and {{high}} hours of effort.',
      hours: '{{value}} hours',
      percentiles: {
        p10: 'Everything goes well',
        p50: 'As likely over as under',
        p80: 'Comfortable',
        p90: 'Cautious',
        p95: 'Very cautious'
      },
      // All five, beside the band and never behind a disclosure: a forecast whose
      // assumptions are one click away is a forecast that gets screenshotted without them.
      // Zeros read a little flatly here, and that is the honest reading — somebody who
      // assumed no common cause and no unlisted work said so, and the number says they did.
      assumptions:
        'Assuming {{capacity}} things under way at once, up to {{worseBy}}% longer in a bad stretch, and {{growthLow}}–{{growthHigh}}% more work than has been listed — over {{samples}} simulated runs.',
      // Beside the number rather than behind a link. The first two below are retired:
      // M3b models what they name, so nothing writes them any more — and the wording stays
      // because the runs a plan made before it are still on this screen.
      limitations: {
        title: 'What this forecast does not do',
        no_team_factor:
          'It treated every task as independent. Real projects have bad weeks where everything runs long together, so the true range was wider than this one.',
        no_scope_uncertainty:
          'It forecast only the work already written down. Projects overrun because of work nobody listed more often than because a listed task ran long.',
        unestimated_items:
          'Some work in this plan carries no estimate. It kept its place in the order and counted as no effort, so the answer is short by whatever it holds.',
        inconsistent_estimates:
          "Somebody's middle number sits a long way from their own two ends. The estimate was used exactly as given; it may be worth a second look.",
        dependencies_on_archived_work:
          'An arrow pointed at work that has been put away. It was left out, because work that is not going to happen cannot be waited for.',
        unknown:
          'This forecast reported something this version of the app cannot describe yet.'
      },
      earlier: {
        title: 'Earlier forecasts',
        // Carries its assumptions, because two runs of one plan made under different ones
        // are not a date moving — and a list that showed only the numbers would read as
        // though they were. That is M10's whole problem, arriving early enough to design
        // around rather than to discover.
        entry:
          '{{middle}} h as likely as not, {{high}} h at the cautious end — {{capacity}} at a time, up to {{worseBy}}% longer in a bad stretch, {{growthLow}}–{{growthHigh}}% more work, asked for by {{who}}.'
      }
    },
    // Shared by the form that starts a project and the form that changes one, because they
    // ask the same two questions at different moments.
    fields: {
      name: { label: 'Project name' },
      description: {
        label: 'What it covers',
        hint: 'Optional, and yours to change later.'
      }
    },
    new: {
      title: 'Start a project',
      lede: 'A container for one plan. Nothing is estimated yet — that comes once there is work in it.',
      submit: 'Create project',
      submitting: 'Creating…'
    },
    // The work inside one plan. No numbers on any of it yet: estimates are the next step,
    // and how they are *asked for* is a milestone of its own.
    items: {
      title: 'Work',
      loading: 'Loading work…',
      none: 'Nothing written down yet. Add the first task below.',
      noneArchived: 'Nothing here has been put away.',
      showArchived: 'Show archived work',
      showCurrent: 'Show current work',
      fields: {
        title: { label: 'Task' },
        description: { label: 'Notes' }
      },
      add: {
        submit: 'Add task',
        submitting: 'Adding…'
      },
      edit: {
        open: 'Edit',
        // What the button is called for anybody reading through a screen reader, where a
        // column of identical "Edit" buttons says nothing about what each one edits.
        openNamed: 'Edit {{title}}',
        submit: 'Save',
        submitting: 'Saving…',
        cancel: 'Cancel'
      },
      archive: 'Archive',
      archiveNamed: 'Archive {{title}}',
      unarchive: 'Bring back',
      unarchiveNamed: 'Bring {{title}} back',
      estimate: {
        open: 'Estimate',
        openNamed: 'Estimate {{title}}',
        // Hours of effort, never a date and never a duration: what a day is worth is a
        // question about calendars and who is available, and that is a later milestone's
        // to answer rather than something to bake into what somebody types here.
        hint: 'In hours of effort — how much work it is, not how long it will be before it is done.',
        fields: {
          p10Hours: 'P10',
          p50Hours: 'P50',
          p90Hours: 'P90'
        },
        submit: 'Save estimate',
        submitting: 'Saving…',
        cancel: 'Cancel',
        none: 'Not estimated',
        mine: 'Your estimate: {{p10}} / {{p50}} / {{p90}} hours',
        // A colleague's range is not invisible just because it is not yours — and it is
        // what makes the coverage count above legible row by row.
        others: 'Estimated by {{names}}'
      },
      // How the plan is joined up. Asked from one end only — the task the form is opened
      // on always finishes first — so there is no way to draw an arrow backwards by
      // misreading a label, and the other direction is the same arrow read from the other
      // task.
      blocks: {
        open: 'Order',
        openNamed: 'Order work around {{title}}',
        hint: 'Pick what cannot start until this is finished. Everything else about the plan follows from these.',
        fields: {
          successorItemId: 'Must finish before',
          // Nothing is chosen to begin with, so the first task in the plan is not quietly
          // the answer for anybody who opens this and presses save.
          choose: 'Choose a task…',
          lagHours: 'Wait afterwards (hours)'
        },
        submit: 'Add',
        submitting: 'Adding…',
        cancel: 'Done',
        drawn: 'Must finish before {{title}}',
        drawnWithLag: 'Must finish before {{title}}, plus {{hours}} hours',
        remove: 'Remove',
        removeNamed: 'Stop requiring this to finish before {{title}}',
        nothingLeft:
          'Nothing left to order this against — every other task in the plan already follows it.',
        // The far end of an arrow drawn against work that has since been put away. The
        // archived listing is a different question, so its titles are not on this screen.
        putAway: 'a task that has been put away',
        // The loop the refusal named, rather than leaving somebody to find it by hand
        // across a plan that can hold five hundred tasks.
        cycle: 'That would make a loop: {{path}}.',
        summary: {
          before: 'Must finish before {{titles}}',
          // Both directions are shown, and neither is redundant: what a delay here would
          // hold up, and why this has not started.
          after: 'Waiting on {{titles}}'
        }
      },
      // What has already happened. Three states and no more: this is not a workflow, and
      // what a forecast needs to know is only whether an item is still ahead of it.
      progress: {
        open: 'Progress',
        openNamed: 'Record progress for {{title}}',
        statusLabel: 'Status',
        status: {
          NOT_STARTED: 'Not started',
          IN_PROGRESS: 'In progress',
          DONE: 'Done'
        },
        // Asked for rather than taken from the clock: work is marked finished on the
        // Monday after it finished at least as often as on the day.
        startedOn: 'Started on',
        completedOn: 'Finished on',
        actualEffortHours: 'Actual effort (hours)',
        // Said before saving rather than discovered after: the boxes holding these have
        // just disappeared, and somebody who does not know why would reasonably assume
        // what was in them is still there.
        clears:
          'Saving this discards the dates and effort already recorded against this task.',
        submit: 'Save progress',
        submitting: 'Saving…',
        cancel: 'Cancel',
        summary: {
          notStarted: 'Not started',
          inProgress: 'In progress since {{started}}',
          done: 'Done {{completed}}',
          doneWithEffort: 'Done {{completed}} · took {{hours}} hours'
        }
      }
    },
    project: {
      loading: 'Loading project…',
      back: '← All projects',
      // Names the half of the page that is about the plan itself rather than its contents.
      details: 'Project details',
      save: 'Save changes',
      saving: 'Saving…',
      saved: 'Saved.',
      archive: 'Archive this project',
      unarchive: 'Bring this project back',
      // Says what archiving is *not*, because the button sits where a delete usually does
      // and the difference is the whole reason it is this button.
      archiveHint:
        'Archiving keeps the project and everything in it, and takes it out of the list.',
      archived: 'Archived. It is out of the list and nothing has been lost.',
      unarchived: 'Back in the list of current projects.',
      archivedNotice:
        'This project is archived. Bring it back to work in it again.'
    }
  },
  members: {
    title: 'Members',
    lede: 'Everyone who can see what {{organisation}} is planning.',
    loading: 'Loading members…',
    // Marks the reader's own row, so removing themselves is a deliberate act.
    you: 'You',
    roleLabel: 'Role for {{name}}',
    remove: 'Remove',
    // What the button is called for anybody reading the page through a screen reader,
    // where a column of identical "Remove" buttons says nothing about what each removes.
    removeNamed: 'Remove {{name}}',
    removeConfirm: 'Remove {{name}} from this organisation?',
    confirmRemove: 'Yes, remove',
    cancel: 'Keep them',
    removed:
      '{{name}} is no longer in this organisation. Their account is untouched.',
    invite: {
      title: 'Invite someone',
      lede: 'They will get a link by email. It works once and lasts a week.',
      roleLabel: 'Role',
      submit: 'Send invitation',
      submitting: 'Sending…',
      sent: 'An invitation is on its way to {{email}}.'
    },
    pending: {
      title: 'Pending invitations',
      none: 'Nobody is waiting on an invitation.',
      expires: 'Expires {{date}}',
      // Still outstanding, and still holding this address's one live slot — so it is
      // something to act on rather than something that has tidied itself away.
      expired: 'Expired {{date}} — send a new link or withdraw it',
      resend: 'Send again',
      resendNamed: 'Send {{email}} a new link',
      resent:
        'A new link is on its way to {{email}}. The previous one has stopped working.',
      revoke: 'Withdraw',
      revokeNamed: 'Withdraw the invitation to {{email}}',
      revoked: 'The invitation to {{email}} has been withdrawn.'
    }
  },
  invite: {
    loading: 'Checking your invitation…',
    title: 'Join {{organisation}}',
    lede: '{{inviter}} has invited you to join {{organisation}} on Aurevanta as {{role}}.',
    // The address already has an account, so it has to be claimed by whoever holds it.
    // Signing in returns here rather than going to the dashboard.
    claimed: {
      lede: 'This invitation is for an address that already has an Aurevanta account.',
      signIn:
        '<signIn>Sign in</signIn> as that person to accept it — you will come straight back here.'
    },
    signOut: 'Sign out and accept it as somebody else',
    create: {
      lede: 'Choose how you want to be known and a password, and you are in.',
      submit: 'Join {{organisation}}',
      submitting: 'Joining…'
    },
    accept: {
      as: 'You are signed in as {{email}}.',
      submit: 'Accept invitation',
      submitting: 'Joining…'
    },
    unusable: {
      title: 'This invitation cannot be used',
      signIn: 'Already joined? <signIn>Sign in</signIn>.'
    },
    joined: {
      title: 'You have joined {{organisation}}',
      body: 'Everything you plan from here belongs to this organisation and is visible only to its members.',
      open: '<app>Open Aurevanta</app> to get started.'
    }
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
      slug_taken: 'Somebody already has that handle. Choose another.',
      invalid_credentials: 'Email or password is incorrect.',
      email_not_verified:
        'Confirm your email address before signing in. Check your inbox for the link we sent when you registered.',
      invalid_token:
        'That link has expired or has already been used. Ask for a new one.',
      not_a_member: 'You do not belong to that organisation.',
      not_an_owner: 'Only an owner of this organisation can do that.',
      last_owner:
        'An organisation must always have at least one owner. Make somebody else an owner first.',
      member_not_found: 'That person is no longer in this organisation.',
      // Says the same thing for a project that never existed and one belonging to another
      // organisation, because the server does — which is what stops it being a way to ask
      // which projects exist elsewhere.
      project_not_found: 'That project is no longer in this organisation.',
      work_item_not_found: 'That task is no longer in this organisation.',
      // Not a complaint about any one of the three boxes — each holds a perfectly good
      // number, and what is wrong is the order they are in.
      estimate_out_of_order:
        'The three numbers must go up: P10 no more than P50, and P50 no more than P90.',
      // Both numbers are perfectly good percentages and what is wrong is which way round
      // they are, so this names the pair rather than picking one of the two boxes to
      // blame — the same reason `estimate_out_of_order` is worded as it is.
      scope_growth_out_of_order:
        'The two growth numbers are the wrong way round: the most a plan grows cannot be less than the usual amount.',
      // Which date is missing depends on the status, so this says both rather than pointing
      // at one box and describing half of what is wrong.
      progress_date_required:
        'Work in progress needs a start date, and finished work needs a date it was finished.',
      progress_out_of_order: 'Work cannot be finished before it was started.',
      // Reached by a client other than this one: the form only offers the boxes a status
      // has room for, so nobody using it can send a claim that contradicts itself.
      progress_not_applicable:
        'Work that has not started records no dates or effort, and work in progress has no date it was finished.',
      // Reached by a client other than this one: the form offers only the tasks that
      // could be picked, and never the task it was opened on.
      self_dependency: 'A task cannot wait for itself.',
      dependency_across_projects:
        'Both ends of a dependency have to be in the same project.',
      dependency_already_exists:
        'That task already has to finish before this one.',
      // The loop itself is named separately, from the `path` the refusal carries — a
      // sentence that only says a loop exists leaves somebody to go and find it.
      dependency_cycle: 'That would make a task wait for itself.',
      dependency_not_found: 'That dependency is no longer in this project.',
      // Not an error so much as a thing to go and do: a forecast of nothing is not a
      // forecast, and the remedy is on the same screen.
      nothing_to_forecast:
        'Nothing in this plan has been estimated yet, so there is nothing to forecast. Add a range to some of the work above.',
      forecast_not_found: 'That forecast is no longer in this project.',
      already_a_member: 'That address already belongs to this organisation.',
      invitation_already_pending:
        'That address has already been invited. Send them a new link instead.',
      invitation_not_found: 'That invitation is no longer outstanding.',
      // Told apart from a withdrawn one because the advice is opposite: one can be sent
      // again, and the other was somebody's decision.
      invitation_expired:
        'This invitation has expired. Ask whoever invited you to send another.',
      invitation_revoked: 'This invitation is no longer available.',
      sign_in_required: 'That address already has an Aurevanta account.',
      invitation_for_another_address:
        'This invitation was sent to a different address.',
      credentials_required:
        'Choose how you want to be known and a password to finish setting up your account.',
      // The server says how long to wait in a Retry-After header, which nothing here
      // reads yet — so the wording stays vague rather than promising a time it does
      // not know.
      too_many_requests:
        'Too many requests just now. Wait a few minutes and try again.',
      // Two writers racing on something the server has no better name for. It used to
      // say "that email address or organisation name was just taken", which was true
      // while registering was the only thing that could produce it and became a guess
      // once every endpoint could.
      conflict: 'Something else changed at the same moment. Try that again.',
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
      // True of a select and of a number alike. It said "choose one of the options" while
      // every field that produced it happened to be a dropdown; the code names the
      // constraint rather than the field, so the message cannot assume the shape of one.
      not_null: 'This is required.',
      size: 'Use between {{min}} and {{max}} characters.',
      // A constraint that only bounds length above; `size` would say "between 0 and 200".
      max_size: 'Use no more than {{max}} characters.',
      email: 'Enter a valid email address.',
      // The server keeps the expression to itself, so this has to say the rule in its own
      // words rather than interpolate one.
      pattern: 'Use lowercase letters, numbers and hyphens.',
      positive: 'Use a number greater than zero.',
      // Zero is a claim that there is no wait, so it is allowed where `positive` is not.
      // A negative one would be a lead rather than a lag, which is a different kind of
      // dependency than the one this product models.
      positive_or_zero: 'Use zero or a number greater than it.',
      // A ceiling on a quantity rather than on a length, which is what `max_size` is for.
      // The only one so far is how many runs a forecast may simulate.
      max: 'Use no more than {{value}}.',
      // The bounds come from the constraint, so this sentence never repeats them.
      digits: 'Use at most {{fraction}} decimal places.',
      invalid: 'Check this and try again.'
    },
    network: 'Could not reach the server. Check your connection and try again.',
    unknown: 'Something went wrong. Please try again.'
  }
} as const;
