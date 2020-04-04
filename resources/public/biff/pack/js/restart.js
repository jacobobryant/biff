function check() {
  fetch("/biff/pack/ping").then(r => {
    if (r.ok) {
      window.location = "/biff/pack";
    }
  });
}
setTimeout(() => setInterval(check, 1000), 3000);
