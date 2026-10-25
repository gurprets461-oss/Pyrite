# Fetch a web page with the standard library (needs internet).
import ssl
import urllib.request

with urllib.request.urlopen("https://example.com", context=ssl.create_default_context(), timeout=20) as r:
    print("Status:", r.status)
    body = r.read().decode()
print(body[body.find("<title>"):body.find("</title>") + 8])
print("Tip: the Packages tab installs `requests` for a nicer API.")
