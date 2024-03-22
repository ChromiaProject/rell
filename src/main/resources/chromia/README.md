# Generated Chromia API Reference Documentation

## Hosting

This generated site includes JavaScript code to provide a navigation bar. While it's possible to open the `index.html` file directly in your browser, the best experience is achieved by hosting the site with a web server.

### Using Docker

If you have Docker installed, you can quickly host the site using an HTTP server container:

```bash
docker run -dit --name my-docs-site -p 8080:80 -v "$PWD":/usr/local/apache2/htdocs/ httpd:2.4
```

### Using Node.js

If you prefer using Node.js, you can host the site with `http-server`:

First, install `http-server` globally:

```bash
npm install -g http-server
```

Then navigate to the directory containing the `index.html` file and run:

```bash
http-server
```

This will start a local web server, and you can access the site at `http://localhost:8080` in your browser.

By hosting the site, you ensure that the navigation bar functions properly and provides easy access to all documentation sections.
