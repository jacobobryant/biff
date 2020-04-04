function check() {
  fetch("/nimbus/pack/ping").then(r => {
    if (r.ok) {
      window.location = "/nimbus/pack";
    }
  });
}
setTimeout(() => setInterval(check, 1000), 3000);
