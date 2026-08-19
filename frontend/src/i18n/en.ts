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
      // "Resources" and not "Team": people are one kind of thing in here, and a staging
      // environment is another. The word has to cover both without implying the screen is
      // about anybody.
      resources: 'Resources',
      // Not "Calibration". The route and the API keep the precise word; the person reading
      // the nav gets the plain one, which is the same rule the estimate form follows when
      // it refuses to print "P90".
      trackRecord: 'Track record',
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
    // wrong. The engine answers in hours of effort; the date beside it is one percentile
    // of that with a working day laid on top — which is why the working day is asked for
    // here and printed back beside every date it produced. A date is the first thing this
    // product emits that looks like a fact, and the hours are what stop it reading as one.
    forecast: {
      title: 'Forecast',
      lede: 'Simulated from the ranges on the work above, then read onto a calendar you state.',
      loading: 'Loading forecasts…',
      // Every box but one is a claim about this team that nobody here can make for them.
      // The exception is the start date, which is filled in because what day it is is a
      // fact rather than a claim — so this says "almost none" rather than "not one", which
      // it used to and which the calendar made untrue.
      none: 'No forecast yet. Answer the questions below and ask for one — almost none of them has an answer this application can give for you.',
      submit: 'Forecast this plan',
      submitting: 'Simulating…',
      more: 'Advanced',
      fields: {
        capacity: {
          label: 'Things that can be under way at once',
          // Not pre-filled, and this hint is why: a box already answered is a box nobody
          // reads, and this is the number that moves the answer most.
          hint: 'Required. The same plan finishes in half the time with twice the people, so nobody can guess this for you.',
          // Absent rather than disabled once a team exists, so the sentence has to say what
          // answers the question instead.
          declared_one:
            'Your one resource says how much can be under way at once, so there is nothing to answer here.',
          declared_other:
            'Your {{count}} resources say how much can be under way at once, so there is nothing to answer here.'
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
        // The only box on this form that arrives already answered, and the distinction is
        // what keeps the rule honest rather than absolute: what day it is is a fact this
        // browser holds and the server does not, where every other box here is a claim
        // about a team. It stays editable, because a plan that starts in January is one
        // edit away.
        startsOn: {
          label: 'Work starts on',
          hint: 'Today unless you say otherwise. A plan that begins later is forecast from then, and a weekend start begins on the Monday.'
        },
        // The number that turns a model's output into something somebody will act on, and
        // the one most likely to be answered for the whole team by mistake — which would
        // produce a date too early by exactly the number of people on it, with nothing on
        // screen looking wrong. The hint is most of the defence.
        workingDay: {
          label: 'Hours in a working day',
          hint: "Required. One person's day, not the team's total — how many people are on it is already the question above."
        },
        sampleCount: {
          label: 'Simulated runs',
          hint: 'Ten thousand unless you say otherwise, which is accurate to well under a percent.'
        }
      },
      // The headline, and the whole reason this milestone exists: nobody asks for a
      // distribution, they ask what to promise. Moving the control moves the date without
      // a request going out, which is not an optimisation but the feature — "can we go
      // faster?" stops being a capitulation and becomes "we can commit at lower
      // confidence", visible in one control while everybody watches.
      confidence: {
        legend: 'How confident do you need to be?',
        option: '{{value}}%',
        // **The line this milestone exists for, and the test of it is whether it can be
        // pasted into an email unedited.** It could not: it named a confidence and a day and
        // never what was being forecast, so it only made sense on the screen it was already
        // on. It also opened with a bare percentage, which reads as a statistic before it
        // reads as a sentence.
        //
        // One-sided, and that corrects `roadmap.md`'s own example: "between 12 October and
        // 20 November" is a two-sided interval, and it invites "so not before the 12th?" —
        // a question about the end of the distribution the model is worst at and nobody
        // manages against. What somebody commits to is a day they will not be past.
        date: 'There is a {{confidence}}% chance that {{plan}} will be finished by {{date}}.',
        // A run made before a working day was something anybody stated. Its hours are
        // still true; backfilling a calendar onto it would have invented a claim nobody
        // made, so it says so in a line instead.
        noCalendar:
          'No date for this one: it was made before anybody stated a working day, so hours are all it can say.',
        // The other direction, and the one that will happen later: the server versions
        // ahead of the browser, so a run made under a calendar this app has never heard of
        // reports its hours rather than being read through the wrong one.
        unreadableCalendar:
          'No date for this one: it was made under a calendar this version of the app cannot read.'
      },
      // Beside the assumptions sentence and never behind a disclosure, because a date is
      // the first thing this product emits that looks like a fact — "14 November" does not
      // advertise that it came out of a model, and it gets pasted into a plan with the
      // assumption behind it left in the browser.
      calendar:
        "Dates assume work starts {{start}}, one person's working day holds {{day}} hours, and nobody works weekends.",
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
      // Five of the six, beside the band and never behind a disclosure: a forecast whose
      // assumptions are one click away is a forecast that gets screenshotted without them.
      // The sixth is the calendar, in its own sentence below because a run made before M4
      // has none and this paragraph would otherwise need writing twice.
      // Zeros read a little flatly here, and that is the honest reading — somebody who
      // assumed no common cause and no unlisted work said so, and the number says they did.
      assumptions:
        'Assuming {{capacity}} things under way at once, up to {{worseBy}}% longer in a bad stretch, and {{growthLow}}–{{growthHigh}}% more work than has been listed — over {{samples}} simulated runs.',
      // Beside the number rather than behind a link. The first two below are retired:
      // M3b models what they name, so nothing writes them any more — and the wording stays
      // because the runs a plan made before it are still on this screen.
      // What the ranges feeding this band have historically been worth. A caveat about the
      // *inputs* and never a correction to the number: applying a calibration factor would
      // close a loop on its own evidence, so this says what the estimates have been worth
      // and leaves the band exactly as the engine produced it.
      trackRecord: {
        line: 'Estimates in this organisation have contained the outcome {{rate}}% of the time, over {{scored}} scored so far.',
        link: 'See the track record'
      },
      // What the plan's own history says, beside what its estimates say.
      //
      // **The gap is the deliverable and it is deliberately not a number.** Two of the four
      // differences below make the engine look slow and two make it look fast, so a
      // subtraction of the two dates is not interpretable on its own — what ships is both
      // dates and what they are not agreeing about. Nothing here averages them or picks one:
      // "six weeks against eleven" starts a conversation and a number in the middle ends it.
      throughput: {
        title: 'What this plan has actually been delivering',
        // Read at whichever confidence the control above is set to, so the two move together.
        date: 'Its own history says {{confidence}}% likely by {{date}}.',
        against: 'The estimates above say {{date}}.',
        // Named rather than computed into a figure, for the reason above.
        differenceLater:
          'The history is {{days}} days later than the estimates — which is the ordinary result, and the reason to look at both.',
        differenceEarlier:
          'The history is {{days}} days earlier than the estimates.',
        differenceSame: 'The two land on the same day.',
        // Decision 12's flag, which the window alone does not carry: between a quarter of
        // history and a year the answer is published *and* marked, because a bootstrap can
        // draw nothing worse than the worst week it has seen and at a quarter roughly one
        // team in four has not yet seen its own. Whether it fires is the server's to decide
        // — the browser holds no threshold, which is `EstimateQuality`'s rule.
        short:
          'This is a short history. It can never produce a week worse than the worst one above, so if your team loses a week now and then and none is in that window, this date is optimistic.',
        // The window, because a reader who knows their team is the only one who can tell
        // whether it contains the bad week they are worried about.
        window:
          '{{weeks}} weeks of history, {{completed}} delivered — best week {{best}}, worst week {{worst}}.',
        remaining_one: '1 item left to deliver.',
        remaining_other: '{{count}} items left to deliver.',
        // The burn-up. **The table is the feature and the drawing is the enhancement**, so
        // every one of these strings describes the numbers rather than the picture — a cone
        // that has to be seen to be understood is one this product cannot ship.
        burnUp: {
          title: 'What has been delivered',
          // The sentence somebody reads out. The two halves are separate because the second
          // needs a projection and the first never does.
          // **One sentence and no second date**, which the review pass corrected: the date
          // this section is about is already on screen one-sided, three lines above, and the
          // two-sided form decision 2 exists to keep out had crept back into the plan's own
          // example for this step.
          delivered: 'Delivered {{delivered}} of {{total}}.',
          // What the table shows, said before it rather than left to be counted. Two counts
          // again, and both reach one: a plan with a week of history, and one the history
          // says finishes inside a week.
          caption: '{{past}}, then {{ahead}} this history projects.',
          captionPast_one: '1 week delivered',
          captionPast_other: '{{count}} weeks delivered',
          captionAhead_one: '1 week',
          captionAhead_other: '{{count}} weeks',
          week: 'Week',
          count: 'Delivered',
          // The band, and it is the column that says a row is a projection rather than a
          // record: the past has no range because it already happened.
          range: 'If it goes better or worse',
          band: '{{low}} to {{high}}',
          projected: 'What the history projects'
        },
        // Decision 7's table. The first two carry this run's own numbers; the last two are
        // properties of the two methods and are the same on every plan.
        differences: {
          title: 'What the two are not agreeing about',
          unlistedGrowth:
            'This forecast assumed the plan will grow by {{low}}–{{high}}%. The history does not model growth at all, so it is short by however much work nobody has written down yet.',
          unlistedNone:
            'This forecast assumed no growth at all. The history does not model growth either, so both are short by however much work nobody has written down yet.',
          unestimated:
            '{{unestimated}} of {{total}} items carry no estimate. The forecast counted them as no effort; the history counts them as items like any other.',
          estimated:
            'Every item carries an estimate, so this is the one difference the two do not have.',
          calendar:
            'The forecast turns effort into days through a working day somebody stated. The history is already in wall-clock weeks, with the holidays and the interruptions inside them.',
          interruptions:
            'Anything that stops the team is in the history by construction, and in the forecast only if somebody put it into the bad-week assumption.'
        },
        // Three ways to have no second date, and each says which rather than showing a gap.
        none: {
          title: 'No second opinion yet',
          throughput_history_too_short:
            'There is not enough finished work here to project from. A quarter of history is where this starts saying something.',
          throughput_nothing_left:
            'Everything in this plan is finished, so there is nothing left to project.',
          throughput_beyond_horizon:
            'At the rate this plan has been delivering, the work left would not be finished within ten years — so no date is given rather than one nobody could act on.',
          unknown:
            'This version of the app cannot say why there is no second opinion.'
        }
      },
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
        // Only ever reported where it can change the answer — with one pool, naming nothing
        // and naming that pool are the same claim.
        unassigned_work:
          'Some work in this plan names no resource. It was scheduled against one unit of whatever happened to be free, so the answer rests on a choice nobody made.',
        requirements_on_archived_resources:
          'Some work asks for a resource that has been put away. It was left out, because a resource the team no longer has cannot be waited for — so the answer is short by however much it was the constraint.',
        dependencies_on_archived_work:
          'An arrow pointed at work that has been put away. It was left out, because work that is not going to happen cannot be waited for.',
        unknown:
          'This forecast reported something this version of the app cannot describe yet.'
      },
      // What the band is made of, and the question it answers. Loaded only when somebody
      // asks, because working it out means replaying the whole run — about half a second
      // at the largest plan this product supports, which is not a cost to put on opening a
      // page nobody may scroll.
      contributions: {
        open: 'What is widening this?',
        loading: 'Working out what moves the finish…',
        title: 'What the spread is made of',
        // The sentence the ranking answers, said plainly rather than left to be inferred
        // from an ordered list.
        lede: 'Tighten the top of this list and the band narrows most.',
        // Never a percentage, and this is why. The shares overlap because a bad quarter is
        // bad for everything at once, so adding them up gives more than the whole — which
        // is exactly the precise-looking wrong number this product exists to replace.
        caveat:
          'These overlap and do not add up to a whole: when a quarter goes badly it goes badly for everything at once, so more than one of these moves at the same time.',
        // The two rows that are not tasks. When either tops the list, the honest reading is
        // that no estimate below it is the problem.
        discoveredWork: 'Work nobody has listed yet',
        teamFactor: 'A bad stretch, affecting everything at once',
        // A kind of source this version has never heard of. The server versions ahead of
        // the browser, and labelling it as one of the kinds we do know would be worse than
        // saying nothing — the same rule the limitations list keeps.
        unknownKind: 'Something this version of the app cannot describe yet'
      },
      // What a piece of work a run was about is *called*, shared by the ranking above and
      // the cuts below. One run, two lists naming the same items: two wordings would be two
      // chances for one of them to start rendering a missing item as a blank.
      work: {
        // Named rather than hidden: a top contributor — or a proposed cut — missing from
        // the plan is what a reader would otherwise go looking for.
        archived: '{{title}} (put away since)',
        // An item the plan no longer holds at all. Nothing deletes work, so this is the
        // shape of a bug rather than an ordinary state — and it says so instead of
        // rendering a blank row.
        // A run made by an engine that no longer reproduces it says so through the
        // ordinary problem catalogue, like every other refusal: the server sends a code
        // and `describeFailure` finds the wording. A second copy here would be a second
        // sentence to keep in step.
        unknown: 'Work no longer in this plan'
      },
      // A date somebody wants, and what it would take. The one place in this product that
      // proposes dropping work, so nearly every sentence here is arranged to stop it being
      // read as an instruction: it weighs, and somebody else decides.
      target: {
        title: 'Can we hit a date?',
        lede: 'Name the day, say how sure you need to be, and tick the work you would be willing to drop. Nothing here changes the plan.',
        // A run made before a working day was something anybody stated cannot be asked
        // about a date at all — the same two absences the band above tells apart, said in
        // the form of the question this panel puts.
        noCalendar:
          'This forecast was made before anybody stated a working day, so it cannot be asked about a date.',
        unreadableCalendar:
          'This forecast was made under a calendar this version of the app cannot read, so it cannot be asked about a date.',
        fields: {
          by: {
            label: 'We want it done by',
            hint: 'A weekend target counts to the Friday before it, since nobody works the weekend.'
          },
          confidence: {
            // Not pre-filled, like every other claim on this panel: 80% is a reading and
            // 95% is a promise, and which of them a date needs is the question being asked
            // rather than something this application can answer for somebody.
            label: 'How sure do you need to be?',
            hint: 'Required, as a percentage. The higher this is, the more it costs to get there.'
          }
        },
        // Decision 1: which work is negotiable is a judgement about its value, and nothing
        // in this product records any. A list the application chose for itself would be
        // recommending that somebody delete work for sitting on the deciding path.
        candidates: {
          legend: 'What could be dropped?',
          hint: 'Only you can say. A task a regulator requires is not a candidate however large it is.',
          // True of both ways it can happen: a forecast older than everything now listed,
          // and a plan whose work has all been put away since. Neither is a state to
          // apologise for, and both have the same way out.
          none: 'None of the work now in this plan was in this forecast, so there is nothing here it could weigh. Ask for a new forecast and there will be.',
          // Stopped at the limit rather than refused afterwards: being told to untick three
          // would be being asked to guess which three mattered.
          limit:
            '{{most}} is as many as can be weighed at once — each one is a whole simulation. Untick one to put something else on the list.'
        },
        submit: 'What would it take?',
        submitting: 'Weighing what each would buy…',
        answer: {
          // First, because everything below it is advice nobody needs when the bar is
          // already met — and a screen leading with work to drop would have proposed a
          // sacrifice before mentioning it was unnecessary.
          met: 'This plan already gets there: {{confidence}}% of its runs came in by that date.',
          short:
            "As it stands, {{confidence}}% of this plan's runs came in by that date.",
          // The stated assumption beside the number it produced, which is M4's rule
          // arriving in the one place where the number is a recommendation.
          budget:
            "That date is {{hours}} hours of work under this run's own calendar, measured over {{simulations}} runs of the plan."
        },
        // The answer to act on: a set that was searched for and measured at every step.
        together: {
          title: 'What it would take',
          lede: 'Each line is where the plan stands with it and everything above it dropped — measured, not added up.',
          step: 'Drop {{what}} — {{confidence}}%.'
        },
        // Why the search stopped. Three endings, because what to do next differs: accept
        // the list, put something else on the table, or ask again with a shorter one.
        endings: {
          met: 'That gets there.',
          nothing_left:
            'Even with all of that dropped the date is still out of reach. Something else has to give — more people, a later date, or work you have not put on this list.',
          budget_spent:
            'That is as far as this looked: each step weighs every remaining candidate, and the search ran out of the runs it is allowed. Ask again with a shorter list to see further.'
        },
        // Never in one column with the list above, and never with plus signs down the side.
        singles: {
          title: 'What each would buy on its own',
          // Before the numbers rather than after them: a reader who has already added two
          // together has already been given the wrong answer.
          lede: 'These do not add up. Two of them often shorten the same path, so dropping both buys far less than the two figures suggest — and the list above is the one that was actually measured.',
          entry: '{{what}} — {{confidence}}%, {{buys}} points better.'
        }
      },
      // `roadmap.md`'s most compelling question, answered with M7's machinery. It weighs
      // and never decides: what a person costs and how long they take to be useful are
      // judgements this application holds none of.
      hiring: {
        title: 'What if we had one more?',
        lede: 'Each of these is the plan simulated again with that many added — measured, not multiplied.',
        which: 'More of which resource?',
        unnamed: 'A resource that is no longer here',
        howMany: 'How many more?',
        weigh: 'Weigh it',
        weighing: 'Simulating…',
        stands: 'As it stands, {{confidence}}% by {{date}}.',
        step_one: 'One more',
        step_other: '{{count}} more',
        buys_one: '1 day sooner — {{date}}',
        buys_other: '{{count}} days sooner — {{date}}',
        // Zero is an answer rather than a rounding error, and the sentence says which.
        buysNothing: 'no sooner at all',
        // The one place this product's own model is optimistic in a way the number cannot
        // show, said beside the number rather than behind a disclosure.
        rampUp:
          'Nobody here ramps up: a resource added is at full rate from the first hour, which no new joiner is. Read these as the best case.',
        cost: 'It cost {{simulations}} simulations, and changed nothing.'
      },
      // What a run was scheduled against, printed with the other assumptions because that is
      // what it is. The run's own team and not today's: hiring somebody does not rewrite what
      // last month's forecast assumed.
      resources: {
        scheduledAgainst: 'Scheduled against {{team}}.',
        pool_one: '{{name}} (1)',
        pool_other: '{{name}} ({{count}})',
        // Put away since, which is said rather than hidden — the plan still assumed it.
        putAway_one: '{{name}} (1, since put away)',
        putAway_other: '{{name}} ({{count}}, since put away)',
        // And one this organisation no longer holds at all, which renders as what it was
        // rather than as a blank.
        gone_one: 'a resource that is no longer here (1)',
        gone_other: 'a resource that is no longer here ({{count}})'
      },
      // **Whether the date keeps moving out.** Never the direction of the last few runs — a
      // plan that is not slipping still moves out one week and in the next, and a rule about
      // direction fires on 86% of plans re-forecast weekly for six months. What is said here
      // is the distance since the oldest run that answered the same question, against the
      // width of the band this plan itself admits to. The threshold is the server's, as
      // `EstimateQuality`'s are; this end renders a flag it was sent.
      drift:
        'This plan has been drifting. At {{confidence}}% it said {{from}} and now says {{to}} — {{out}}, against a band {{band}}.',
      // **Two counts in one sentence, so the two halves that carry a number are their own
      // entries.** A single `_one`/`_other` pair selects on one `count` and there are two
      // here — and both reach 1, which is what "1 days out, against a band 1 days wide"
      // would have read as. Composing a sentence from fragments is a localisation smell and
      // is bounded here by English being the only catalogue.
      driftOut_one: '1 day out',
      driftOut_other: '{{count}} days out',
      driftBand_one: '1 day wide',
      driftBand_other: '{{count}} days wide',
      // Why the date moved, between this forecast and an earlier one. Loaded only when
      // somebody asks, because it costs six whole simulations.
      movement: {
        open: 'Why did the date move?',
        loading: 'Working out why the date moved…',
        // No heading over the account: the first sentence of it *is* the title, and a
        // heading repeating "why the date moved" above "this plan moved out 8 days" would
        // be the same words twice inside one bullet.
        movedOut:
          'At {{confidence}}% this plan moved out {{days}} days: it said {{from}} and now says {{to}}.',
        movedIn:
          'At {{confidence}}% this plan came in {{days}} days: it said {{from}} and now says {{to}}.',
        movedNot:
          'At {{confidence}}% the date has not moved: {{date}} then and now.',
        // Named as things somebody did rather than as fields, and in the order the server
        // attributed them — which is a rule with a name, because two defensible orders split
        // the same eight days differently.
        steps: {
          SAMPLING: 'Running the simulation again',
          PROGRESS: 'Work reported since',
          ESTIMATES: 'Estimates revised',
          SCOPE: 'Work added or put away',
          ASSUMPTIONS: 'Assumptions changed',
          CALENDAR: 'The working day changed',
          STARTS_ON: 'Time passing'
        },
        termLater_one: '{{count}} day later',
        termLater_other: '{{count}} days later',
        termEarlier_one: '{{count}} day earlier',
        termEarlier_other: '{{count}} days earlier',
        termNone: 'nothing',
        // A run this browser cannot resolve a date for. Not reachable from this screen, which
        // only offers the question where both runs have dates — but the server versions ahead,
        // and a term rendered as a blank would read as a term of nothing.
        termNoDays: 'not in days',
        // Decision 6 and its price, in one line: the terms add up *because* each was measured
        // with the ones above it already applied, and that is what the simulations bought.
        cost: 'Each of these was measured with the ones above it already applied, which is why they add up to the whole move. It cost {{simulations}} simulations.'
      },
      earlier: {
        title: 'Earlier forecasts',
        // Carries its assumptions, because two runs of one plan made under different ones
        // are not a date moving — and a list that showed only the numbers would read as
        // though they were. That is M10's whole problem, arriving early enough to design
        // around rather than to discover.
        entry:
          '{{middle}} h as likely as not, {{high}} h at the cautious end — {{capacity}} at a time, up to {{worseBy}}% longer in a bad stretch, {{growthLow}}–{{growthHigh}}% more work, asked for by {{who}}.',
        // The calendar belongs here for the same reason the other five assumptions do:
        // two runs read under different working days are two readings, not a date moving.
        // Absent on a run that had none, which is what the history is for saying.
        calendar: '{{day}}-hour days from {{start}}.'
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
        // One question at a time, and the percentile names appear in none of them. "P90"
        // asks somebody to reason about tail probability, which nobody can do; surprise is
        // a thing people recognise. The three boxes these replaced produced 3/5/8 without
        // anybody thinking, and no check can catch that — a Fibonacci triple agrees with
        // itself almost perfectly. The order is the defence, so it is fixed: the bad case
        // is the only one of the three with nothing above it, and it is answered before
        // any number is on screen to anchor it.
        steps: {
          bad: {
            question:
              'Think of a version of this that goes badly — not a disaster, just a bad week. What number would make you genuinely surprised to have gone over?',
            hint: 'Asked first and on its own. It is the number teams get wrong most often, and the only one with nothing above it.'
          },
          good: {
            question:
              'Now the version where everything goes right. What is the least this could take?',
            hint: 'Not an impossible case — the one where nothing gets in the way.'
          },
          typical: {
            question: 'And what do you actually expect it to take?',
            hint: 'The middle: as likely to come in under as over.'
          }
        },
        progress: 'Question {{step}} of {{total}}',
        // The first and only moment the three are seen together. Together *while* one is
        // still being answered is the anchoring the order exists to prevent; together once
        // all three exist is the only way to notice that the bad week is barely worse than
        // the ordinary one.
        review: {
          title: 'What you have said',
          // The answer is interpolated whole rather than as a bare number, because a
          // question nobody answered has no number and "not answered hours" is not a
          // sentence. Both readings have to be grammatical, and a unit assembled outside
          // the catalogue would be a literal string in the code.
          bad: 'A bad week: {{answer}}',
          good: 'Everything goes right: {{answer}}',
          typical: 'What you expect: {{answer}}',
          hours: '{{value}} hours',
          unanswered: 'not answered',
          progress: 'Before you save it'
        },
        // The betting frame, which is a check on an answer rather than a way to get one —
        // it needs a number to bet about, so it can only be asked here. It makes a number
        // typed cheaply feel expensive, and it gates nothing: saying yes is pressing save.
        bet: {
          question:
            'You are saying that nine times in ten this comes in under {{hours}} hours. Would you take that bet?',
          decline: 'No — let me change that'
        },
        // Advice and never a refusal. A tight band is sometimes exactly right, and a rule
        // that blocked one would become a specification people learn to type — which is
        // 3/5/8 with an extra step, and the product teaching the failure it exists to
        // detect. Both say plainly that the estimate is saved as given.
        warnings: {
          overconfident:
            'Your bad week is barely worse than what you expect, which usually means nothing has yet been thought of that could go wrong. Saved exactly as you gave it, either way.',
          inconsistent:
            'What you expect sits a long way from the middle your own two ends imply. Worth another look — and saved exactly as you gave it, either way.'
        },
        back: 'Back',
        next: 'Next',
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
      // What a piece of work needs before it can be under way. Asked as a whole set,
      // because a requirement means little alone — and an empty set is a claim rather than
      // an omission, which is why the form says so where somebody is deciding.
      needs: {
        open: 'Needs',
        openNamed: 'Say what {{title}} needs',
        lede: 'How many of each does this tie up while it is being done?',
        available: '{{units}} available',
        anyone:
          'Leave them all empty and anybody can pick this up — it will take one of whoever is free.',
        noResources:
          'Nothing has been declared to need yet. Add your team and equipment on the resources page, and this will ask which of them this work ties up.',
        submit: 'Save what it needs',
        cancel: 'Cancel'
      },
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
        // Asked for and never required — V10 settled that, because refusing to let somebody
        // mark work finished until they can say how long it took would refuse the common
        // case. What the hint does is say what the box is *for*: it is the only number the
        // track record can compare a range against, and a range nobody checks is a range
        // nobody learns from.
        actualEffortHint:
          'Optional, and the one number your track record is built from — without it there is nothing to compare the estimate against.',
        // Said before saving rather than discovered after: the boxes holding these have
        // just disappeared, and somebody who does not know why would reasonably assume
        // what was in them is still there.
        clears:
          'Saving this discards the dates and effort already recorded against this task.',
        // Who last said something about this task, from a log that keeps every claim
        // rather than only the newest. One line rather than a history screen: what it is
        // for is that somebody can tell whether the state in front of them is theirs — and
        // that a record nothing ever reads is a record that quietly stops being written.
        lastReported: 'Last reported by {{name}} on {{day}}.',
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
  // How often this organisation's ranges contained the truth.
  //
  // **Nothing here names a percentile**, which is the estimate form's rule reappearing:
  // "landed inside the range" says the same thing to more people than "P10–P90 coverage",
  // and nobody can reason about tail probability whether they are answering or reading.
  // Where a position inside somebody's own range has to be shown, it is shown as a position
  // — a marker on a bar between the two ends they were asked for — rather than as a number
  // that would have to be explained.
  //
  // **And no threshold lives here.** Whether a record is good is not a judgement the browser
  // may make: two rules about one estimate is what `EstimateQuality` exists to prevent, so
  // this page states what a well-judged set scores and shows what this one scored, and lets
  // the reader do the subtraction.
  resources: {
    title: 'Resources',
    lede: 'What this organisation has to work with. A forecast schedules against these rather than against a number, so work that needs a particular one waits for it.',
    loading: 'Loading resources…',
    none: 'No resources yet. Until there are, a forecast asks how much can be under way at once.',
    noneArchived: 'Nothing has been put away.',
    showArchived: 'Show what has been put away',
    showCurrent: 'Show what is in use',
    // The unit count is the whole of what a pool is, so it is in the line rather than
    // beside it.
    entry_one: '{{name}} — 1 unit',
    entry_other: '{{name}} — {{count}} units',
    person: 'This is {{name}}.',
    change: 'Change',
    changing: 'Changing…',
    putAway: 'Put away',
    bringBack: 'Bring back',
    fields: {
      name: { label: 'What is it called?' },
      units: {
        label: 'How many are there?',
        // Occupancy and never speed, which is the one thing about this number somebody
        // is likely to assume the other way round.
        hint: 'Whole units. Two people on one task means it ties up two of them, not that it goes twice as fast.'
      },
      person: {
        label: 'Is this a particular person?',
        nobody: 'Not a particular person',
        hint: 'Optional, and only a label — nothing here says what anybody is working on.'
      }
    },
    new: {
      title: 'Add a resource',
      lede: 'A pool of people, an environment, a licence — anything a task has to have before it can start.',
      submit: 'Add it',
      submitting: 'Adding…'
    }
  },
  calibration: {
    title: 'Track record',
    lede: 'How often the ranges written here contained what the work actually took.',
    loading: 'Loading your track record…',
    // The target, stated beside the number, because 45% means nothing without it — and
    // because 100% is not the target and somebody has to be told so.
    target:
      'A well-judged set of ranges contains the outcome about 8 times in 10. Higher is not better: ranges wide enough to contain everything predict nothing.',
    headline: {
      title: 'Estimates written before the work began',
      rate: '{{rate}}% contained what the work actually took',
      // The count and the interval travel with the rate and never below it. Four out of
      // five is 80% and says nothing whatever; this is the sentence that says so.
      confidence:
        '{{hits}} of {{scored}} · likely between {{low}}% and {{high}}%',
      tails:
        '{{above}} ran past the top of the range, {{below}} came in under the bottom.',
      // A rate with one outcome behind it has no spread to report, and a bias reported with
      // no spread beside it is the half of this record that reads as a target to hit.
      tooLittle:
        'Not enough finished work yet to say whether these ranges sit high or low, or how wide they should have been.'
    },
    // The two corrections, and they are shown together or not at all. A hit rate on its own
    // is gamed in one move — estimate everything one to a thousand hours and score 100%
    // forever — and the width below is what reports that as a number under one.
    corrections: {
      title: 'What that says about the ranges themselves',
      // Shown as a position rather than as a number, so that nobody has to be told what a
      // percentile is. The two ends are the two questions the estimate form actually asks.
      biasTitle: 'Where the work usually lands in your own range',
      good: 'Good case',
      bad: 'Bad case',
      biasReading:
        'Half the work lands above this point. Well-judged ranges put it in the middle.',
      widthTitle: 'How wide the ranges should have been',
      widthReading:
        'These ranges would have had to be {{multiplier}} times as wide to have contained the outcome as often as they claimed. Well-judged ranges read 1.0.',
      // Not an edge case: three identical numbers is somebody saying they are certain, and
      // M2 accepts it on purpose. It counts in the rate above and cannot count in either
      // reading here, so the two denominators differ and this says by how much.
      certain_one:
        '1 estimate claimed certainty and is counted in the rate above but not here.',
      certain_other:
        '{{count}} estimates claimed certainty and are counted in the rate above but not here.'
    },
    // The three buckets, stacked rather than summed. They are one question asked of ranges
    // written at three moments, and the second is expected to be very good precisely because
    // hindsight is not a skill.
    buckets: {
      title: 'When each range was written',
      lede: 'These are three separate answers about three sets of estimates. They do not add up to anything.',
      forecasts: 'Before the work began',
      forecastsHint:
        'The only ones that were predictions. Everything above is these.',
      reports: 'After the work began',
      reportsHint:
        'Written by somebody who could already see how it was going. Expect these to be very good; how much better than the forecasts is the size of hindsight on your own work.',
      unbounded: 'Work with no start date reported',
      unboundedHint:
        'Nobody said when this work began, so these cannot be told apart from the ones above — named rather than quietly counted as predictions.',
      nothing: 'None yet',
      // Two short lines rather than one long one. The rate leads, and under it the
      // interval and the count — because a rate without its interval is the half of it
      // that means nothing, and that rule holds on a row as much as on the headline.
      // Short, because the heading above has already said what the number means:
      // repeating that on every row pushed the labels into wrapping and made three tidy
      // rows read as a paragraph.
      figure: '{{rate}}%',
      figureDetail_one: '{{low}}–{{high}}% · 1 estimate',
      figureDetail_other: '{{low}}–{{high}}% · {{count}} estimates'
    },
    // What V15 exists for, and the only evidence M5's claim will ever have.
    methods: {
      title: 'By how the range was asked for',
      lede: 'The question changed in August 2026. Whether that produced honester ranges is a thing only this table can answer.',
      three_point: 'Three boxes, filled in together',
      surprise_framed: 'One question at a time, bad case first',
      unknown: 'Asked in a way this page does not recognise'
    },
    // Named, never ranked. A hit rate sorted best-first is a leaderboard, and a hit-rate
    // leaderboard is won by writing one to a thousand — the failure this whole page exists
    // to expose. So: alphabetical, with the count beside every row.
    estimators: {
      title: 'By estimator',
      lede: 'In name order, not in any order of merit — and with the count beside each, because six outcomes are not ninety.'
    },
    // The main screen of this page for most of its first year, and written as such: each
    // line is a different thing to go and do rather than an apology.
    coverage: {
      title: 'What is not being scored',
      nothingFinished:
        'Nothing has been finished here yet. A track record starts with work that is done, estimated, and measured.',
      noActual_one:
        '1 finished task did not record how long it took. That is the number that has to change: without it there is nothing to compare a range against.',
      noActual_other:
        '{{count}} finished tasks did not record how long they took. That is the number that has to change: without it there is nothing to compare a range against.',
      noEstimate_one:
        '1 finished task was never estimated, so there was no prediction to be right or wrong about.',
      noEstimate_other:
        '{{count}} finished tasks were never estimated, so there was no prediction to be right or wrong about.',
      scored_one: '1 finished task is being scored.',
      scored_other: '{{count}} finished tasks are being scored.',
      // Decision 1's price, published rather than absorbed. An estimate is a moment and a
      // start is a day, so an estimate written at any hour of the start day is counted as a
      // report — which costs real forecasts, and the cost is stated instead of hidden.
      startDay_one:
        '1 finished task would have counted as a prediction if an estimate written on the day the work began counted as one. A start date is a day and an estimate is a moment, so the two cannot be told apart within it.',
      startDay_other:
        '{{count}} finished tasks would have counted as predictions if an estimate written on the day the work began counted as one. A start date is a day and an estimate is a moment, so the two cannot be told apart within it.',
      span: 'Covering estimates written between {{first}} and {{last}}.'
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
      // One answer for "there is no such pool" and "it belongs to another organisation",
      // as the plan and item codes are: telling them apart would make the endpoint a way
      // of discovering which identifiers exist elsewhere.
      resource_not_found: 'That resource is no longer in this organisation.',
      // A pool declared since the run: a counterfactual is only meaningful against the plan
      // that was actually forecast, so the remedy is a new forecast rather than a retry.
      resource_not_in_forecast:
        'That resource was not part of this forecast. Ask for a new one and it will be.',
      forecast_has_no_resources:
        'This forecast was made against a capacity rather than a team, so there is nothing to add to. Forecast it again once your resources are described.',
      // About the person somebody named, and never about the caller — which is why it is
      // not `not_a_member`. One of those would have somebody re-authenticating over a
      // mistyped colleague.
      person_not_a_member:
        'That person is not in this organisation, so a resource cannot be named after them.',
      // Two lines for one pool are two spellings of one number, and a scheduler adding
      // them up would read a data-entry mistake as a claim about a team.
      duplicate_requirement:
        'That work already needs that resource. Say how many units it needs on one line rather than two.',
      // **Reachable through this product's own screens, which a comment here once denied.**
      // The progress form's date input carries no upper bound, so anybody can record a task
      // as finished next week — and a plan holding one loses its throughput answer entirely
      // until somebody corrects the date. `roadmap.md` carries that under *Dates the schema
      // accepts and reality does not*; until it is fixed this wording is what a reader gets,
      // so it says what to go and look for rather than that something went wrong.
      throughput_out_of_order:
        'Some work in this plan is marked as finished on a day that has not happened yet, so its delivery history cannot be read. Correct the completion date on that task.',
      // A run the engine no longer reproduces exactly. Breaking it down would mean ranking
      // a plan under a model that never forecast it — which would look entirely reasonable,
      // and is why the server refuses rather than approximates.
      // Two runs made by different versions of the model. M6's argument rather than a fussy
      // check: an account of a movement between two models is an exact account of a movement
      // that never happened, and it would look entirely reasonable.
      // Bean Validation cannot ask whether a capacity is needed, because that depends on
      // whether the organisation has described its team. Both of these come from the
      // service, and both are about a field that should not be on the screen at all in one
      // of the two states.
      capacity_required:
        'Say how much can be under way at once, or describe your team on the resources page.',
      capacity_not_applicable:
        'Your resources already say how much can be under way at once, so this forecast cannot name a capacity as well.',
      forecast_not_comparable:
        'These two forecasts cannot be compared: they were made by different versions of the model, so the distance between them is not a plan that moved.',
      forecast_replay_mismatch:
        'This forecast cannot be broken down: it was made by an earlier version of the model, which no longer reproduces it exactly.',
      // A run made before a working day was something anybody stated. It reports hours and
      // no dates, so there is no date for it to be asked about either.
      forecast_has_no_calendar:
        'This forecast has no calendar, so it cannot be asked about a date. Ask for a new one and it will have.',
      // Work written down since the run, or belonging to another plan. Not offered on
      // screen, so reaching this means the plan changed while somebody was reading it.
      candidate_not_in_forecast:
        'Some of that work was not in this forecast. Ask for a new one and it will be.',
      too_many_candidates:
        'That is more work than can be weighed at once. Each candidate is a whole simulation, so try again with fewer.',
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
      // How many runs a forecast may simulate, how far either uncertainty parameter is
      // worth asking, and how many hours a day can hold.
      max: 'Use no more than {{value}}.',
      // The bounds come from the constraint, so this sentence never repeats them.
      digits: 'Use at most {{fraction}} decimal places.',
      invalid: 'Check this and try again.'
    },
    network: 'Could not reach the server. Check your connection and try again.',
    unknown: 'Something went wrong. Please try again.'
  }
} as const;
