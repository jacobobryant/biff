# Biff

> *Why don't you make like a tree and get out of here?*<br>
> *It's* leave, *you idiot, make like a tree and leave!*
>
> &mdash;Biff Tannen in Back to the Future

Biff is a web framework and self-hosted deployment solution for Clojure. It's
the culmination of 18 months I've spent building web apps with various
technologies like Datomic, Fulcro, AWS and Firebase (and plenty of Clojure
libraries). I've taken parts I liked and added a few innovations of my own.
It's meant primarily to speed up development in pre-growth startups and hobby
projects, but over time I'd like to make it suitable for apps that need scale
as well.

It includes features for:

- **Installation and deployment** on DigitalOcean.
- [**Crux**](https://opencrux.com) for the database.
- **Subscriptions**. Specify what data the frontend needs declaratively, and
  Biff will keep it up-to-date.
- **Read/write authorization rules**. No need to set up a bunch of endpoints
  for CRUD operations. Queries and transactions can be submitted from the
  frontend as long as they pass the rules you define.
- **Database triggers**. Run code when documents of certain types are created,
  updated or deleted.
- **Authentication**. Email link for now; password and SSO coming later.
- **Websocket communication**.
- Serving **static resources**.
- **Multitenancy**. Run multiple apps from the same process.

Biff is currently **alpha quality**, though I am using it in production for <a
href="https://findka.com" target="_blank">Findka</a>. Join `#biff` on <a
href="http://clojurians.net" target="_blank">Clojurians Slack</a> for
discussion. I want to help people succeed with Biff, so feel free to ask for
help and let me know what I can improve. If you'd like to support my work and
receive email updates about Biff, <a href="https://findka.com/subscribe/"
target="_blank">subscribe to my newsletter</a>.

## Usage

See the documentation at [biffweb.com](https://biffweb.com) (work in progress!).

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
