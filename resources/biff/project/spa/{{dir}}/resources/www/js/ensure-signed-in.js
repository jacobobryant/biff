fetch("/api/signed-in").then(response => {
  if (response.status != 200) {
    document.location = "/";
  }
});
