# biff.fx (alpha)

NOTE: this whole thing so far has been entirely ai-generated (except for this paragraph). I need to make a pass over it before it's ready to be officially released.

An effect system for Clojure web applications. Application logic is expressed as
pure state machines; effects (HTTP requests, database queries, etc.) are
declared as data and executed by the framework between state transitions.
