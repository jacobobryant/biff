---
title: Homework
---

Congratulations—you are now a certified Biff expert! If you'd like to get some more practice,
here are some ideas for ways you can further improve eelchat:

 - Settings for users, communities, and channels. Make it so you can pick names
   instead of being stuck with the default "Channel #108" etc.
 - Do something about the community page. It still shows the dummy "Messages
   window" and "Compose window" elements. One option is to make the community
   page redirect to the first channel, although you'll need to handle the case
   of when there aren't any channels. Another option is to have an "About"
   page, editable by the community admin. This could be visible both to people
   who have joined the community and people who haven't yet.
 - Let people view the community page even if they're not signed into eelchat.
   There should be a signup form that both creates an eelchat account for them
   and adds them to the community in question.
 - Public/private setting for channels. Public channels should be viewable both
   by (1) people who are signed into eelchat but aren't a member of the
   community, and (2) people who aren't signed into eelchat at all. By default,
   channels should be set to private.
 - Refactor the routes. Some of the URLs are quite long, e.g.
   `/community/.../channel/...`. Change those routes to be simply
   `/channel/...` etc, and then modify your middleware so that it infers the
   community from the channel.
 - And here's a big one: add new types of channels. There should three types:
   chat, forum, and threaded. Chat is what we have now. In forum channels,
   messages should be organized into topics with a linear list of replies.
   Threaded channels are similar, except the replies should be displayed in
   hierarchy.

Whether you choose to continue fiddling around with eelchat or start working on
your own projects, the [reference docs](/docs/reference) and
[API docs](/docs/api)
should come in handy. I also recommend reading
[Biff's source](https://github.com/jacobobryant/biff/blob/master/src/com/biffweb.clj)
at your leisure. It isn't all that long (it's about 2.5x the size of eelchat),
and there's no better way to deepen your understanding of Biff.
