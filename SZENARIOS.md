Three Scenarios for BadenConnect
Ordered by priority, with the first being the headline scenario for your demo and implementation focus.

Scenario 1: Startup finds a neighboring startup or expert it didn't know existed
Persona: Lisa, founder of a 6-person Baden startup building an IoT product for facility management. She needs short-term help with embedded firmware optimization — maybe two weeks of work, maybe an ongoing collaboration.
The problem
Lisa is certain that somewhere in PowerPlant or among the dozens of startups in the Baden region, there's someone with exactly the firmware expertise she needs. The competence exists physically nearby. She just has no way to know it exists. Her own team's network doesn't reach into that corner of the ecosystem.
This is Norbert's core framing: "a person who is nearby, but whom you don't know exists." The platform's job is to digitally replicate walking into the office next door.
How it gets done today
Lisa relies on Vitamin B:

Asks her co-founders if they know anyone
Posts in a few Slack/WhatsApp founder groups
Goes to the next PowerPlant networking event and hopes the right person is in the room
Eventually posts on LinkedIn — gets recruiter spam, not the right match
After 2–3 weeks of asking around, either gives up, hires too senior/expensive externally, or stumbles into someone by chance

The expertise was 200 meters away the whole time. Nobody connected the dots.
Proposed flow on the platform

Lisa posts an inserat (Ricardo-style): "Looking for embedded firmware engineer with experience optimizing power consumption on ARM Cortex-M. ~2 weeks initial scope, possibly ongoing. Baden-based preferred." — three fields, two minutes.
The inserat is publicly visible — no login required to read it. A founder at the startup next door scrolling the platform sees it.
In parallel, the platform proactively surfaces matches: it searches indexed startups (Handelsregister-anchored), public personal sites, GitHub profiles linked through registered companies, and any opted-in profiles. It returns 3–5 candidates with text-based explanations: "Marco at NextGen Sensors — his company's public project descriptions mention low-power ARM firmware; his GitHub shows three repos on Cortex-M power profiling."
Lisa contacts directly, or Marco reacts to the inserat. No HR funnel, no application form — just a connection.
Subscribers get notified: anyone subscribed to "embedded" or "firmware" tags sees the inserat in their feed.

Why it's first
It's the scenario Norbert and Nitish explicitly asked you to concentrate on. It's also where the inserat model carries the most weight — meaning you can demo real value even before the harvester stack is fully wired up. The matching layer makes it better, but the inserat alone already beats Vitamin B.

Scenario 2: Researcher finds an industry partner to validate and fund a research idea
Persona: Norbert himself — a researcher at FHNW IIT with a paper on data-driven personas. He wants to find an industry partner (KMU) who could benefit from the approach, ideally co-write an Innosuisse proposal, and turn the research into a real-world validation.
The problem
Norbert has a concrete research idea with industry relevance. He needs:

A KMU that has the right kind of product/data to apply the method
A KMU that's aware such collaboration is even possible (many aren't aware Innosuisse funding exists)
A way to find them without cold-emailing through LinkedIn for weeks

His own framing in the interview: "I've never explicitly searched. The projects came to me through channels and contacts." That's a sign the discovery side is genuinely broken for researchers, not just for industry.
How it gets done today

Asks colleagues "do you know any companies in X domain?"
Scans LinkedIn ad-hoc, with no systematic approach
Brings it up at conferences and networking events, hoping the right person happens to be in the audience
Most projects he ends up with came to him through existing contacts, not through his own outbound search
Result: research-industry collaboration is heavily dependent on who you already know — exactly the Vitamin B problem from the other side

Proposed flow on the platform

Norbert posts an inserat describing the research opportunity: "Looking for a KMU with an active software product and existing user analytics, interested in evolving their personas using behavioral data. Potential Innosuisse co-application." He attaches or links the paper.
Companies anchored in the Handelsregister and matched against domain/keywords get surfaced as potential targets — not for cold-mailing them through the platform, but to give Norbert a shortlist with rationale: "This company's public materials mention persona-driven product design"; "this company has previously partnered on Innosuisse projects."
Companies subscribed to relevant tags (e.g. "user research", "product analytics", "Innosuisse") get the inserat in their feed — including companies that didn't know this kind of collaboration was even on offer. This addresses Norbert's awareness gap directly.
Reverse direction also works: a company posts "we want to better understand our users but don't have a research partner" — the platform surfaces Norbert's profile (built passively from his ORCID, Google Scholar, FHNW page, personal site) with a text rationale showing the relevance.
Explainability is critical here: when a researcher's profile is surfaced for a non-obvious match (Norbert's UX work being relevant for multimodal mobility, his actual example), the rationale must show the bridging concept — not just a similarity score.

Why it's second
It's the scenario closest to the original brief and the one where your harvester stack (OpenAlex, ORCID, ROR, IRF FHNW) is most directly load-bearing. It also has a clear champion in Norbert, which makes evaluation easier. But the meeting clearly signaled this is no longer the headline use case.

Scenario 3: Industry partner needs an expert under time pressure (academic or freelancer)
Persona: Andreas, project lead at a mid-sized Aargau industrial company. A current project just hit a wall — they need someone with deep expertise in, say, multimodal mobility or a specific ML technique, for two months. Hiring a full-time employee is overkill and too slow. A consultancy is too expensive and too generic.
The problem
Andreas knows roughly what expertise he needs but doesn't know the Swiss research and freelancer landscape well enough to find the right person quickly. He doesn't know which FHs work on what, which professors are open to short consulting engagements, or which freelancers in the canton actually have the depth he needs.
How it gets done today

Googles "multimodal mobility expert Switzerland" and lands on a few university pages
Reads through department pages trying to figure out who's actually relevant
Sends a few emails into generic info@ addresses, gets slow replies or none
Asks his network — but his network is industry-side, not academic
Often defaults to either a large consultancy (expensive, generic) or doing without and shipping a worse outcome

This is exactly Norbert's example with 2IM and the multimodal mobility expert: "there is in fact someone at the FH who works on multimodal mobility. It came as a recommendation. We didn't actively know."
Proposed flow on the platform

Andreas enters a freitext query: "We're building a multimodal mobility platform combining private cars and public transit. We need someone who understands Swiss public transit ticketing, timetable APIs, and routing. ~2 months engagement."
The platform applies semantic search across academic profiles, project listings, and (if available) freelancer profiles, with FHNW prioritized first, then Switzerland-wide as a scope control.
Results include text rationales: "Prof. X works in mobility research; her recent project Y dealt with multimodal routing. Has prior consulting experience based on her CV."
Andreas can either contact directly or post a public inserat if he wants the opportunity to find him — same Ricardo model, just from the industry side.
For known competence gaps (e.g. people who don't publish papers but have strong GitHub work), the inserat path is the fallback that catches them.

Why it's third
It's important — and validates the original brief's framing of cross-FH and inter-institutional collaboration — but it overlaps significantly with Scenarios 1 and 2 in terms of platform mechanics. If you build well for 1 and 2, this one largely falls out for free. It's also more sensitive to data completeness, which is a known weakness right now.
