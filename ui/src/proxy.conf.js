const PROXY_CONFIG = {
  "**": {
    target: "http://localhost:20009",
    changeOrigin: true,
    secure: false,
    bypass: function (req) {
      if (req && req.headers && req.headers.accept
        && req.headers.accept.indexOf("html") !== -1 && req.url.indexOf("/report/")===-1
      ) {
        console.log("Skipping proxy for browser request.  url:"+req.url)
        return "/index.html"
      }
    }
  }
}

module.exports = PROXY_CONFIG
