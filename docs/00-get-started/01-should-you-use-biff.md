---
title: Should you use Biff?
---

The more the following points apply to you, the more likely Biff is to be a good fit:

- You're a solo developer (hobbyist, indie hacker, startup founder).
- You're unopinionated about your app's structure and library choices; you'd rather focus on your application logic.
- You're still learning the ropes of Clojure web dev.
- You enjoy top-down learning: starting with a complete system and then figuring out how it works.
- You prefer writing backend code and/or would prefer not to deal with a SPA.

If you're in a team context, Biff could very well still work, but I don't consider it to be tried-and-true for teams
yet. For solo developers, my own experience with Biff—and the positive feedback I've received from others—is enough for
me to confidently recommend it.

These essays provide some additional background info that might help you further gauge if Biff is right for you:

- [Philosophy of Biff](https://biffweb.com/p/philosophy-of-biff/)
- [Understanding htmx](https://biffweb.com/p/understanding-htmx/)
- [XTDB compared to other databases](https://biffweb.com/p/xtdb-compared-to-other-databases/)

There are several non-Biff frameworks/approaches that I also recommend:

[Roll your own framework](https://ericnormand.me/guide/clojure-web-tutorial). Good if you want to master the
fundamentals of Clojure web dev and don't mind spending the time to do so. Also good if you're already familiar with the
Clojure ecosystem and know what pieces you want.

[Kit](https://kit-clj.github.io/), "a lightweight, modular framework for scalable web development." Successor to the
Luminus framework. It has some of the same goals as Biff but with more emphasis on
[performance](https://nikperic.com/2022/01/08/why-kit.html) and
[modularity](https://yogthos.net/posts/2022-01-08-IntroducingKit.html).

[Fulcro](https://fulcro.fulcrologic.com/), a framework for "single-page full-stack web applications in clj/cljs." Tames
the complexities of SPA development, even for very large apps/organizations, but has a steep learning curve. Good if
you're more concerned about maintaining velocity as your project grows than about getting started quickly.
