# Sudoku website sources

`docs/site/` contains only the six Sudoku-owned files: product page, privacy policy, support page and three actual iPhone preview captures. Existing Android imagery, global styles and other products belong to the shared Freevia Pages site.

Build and check with Python 3 and the pinned renderer:

```sh
python3 -m pip install -r tools/site/requirements.txt
python3 tools/site/build-site.py
python3 tools/site/build-site.py --check
python3 -m unittest discover -s tools/site -p 'test_*.py'
```

The policy body comes from `docs/privacy-policy.md`; the support guide includes `docs/ios-user-guide.md`. Product-page templates retain the existing design and clearly identify iOS as in preparation. The three preview PNGs are byte-identical to their App Store preparation sources. No private corpus photographs are included.

## Deployment to the shared Pages project

The existing `freevia` project uses Direct Upload. Upload this subset to a **preview** branch using Wrangler. Read its uploaded path/hash manifest and the current canonical production deployment's full file manifest through the Cloudflare Pages API. Overlay only the six explicitly allowed paths. Create a complete preview deployment with the combined manifest and the existing global `_headers` and `_redirects` configurations; inspect the three pages and image loading.

For publication, read the canonical production deployment again immediately before creating the deployment, overlay only the same six uploaded hashes, and use branch `main`. This preserves concurrent updates to other products. Verify the new canonical deployment status, the six expected changes, equality of every other production asset hash, public HTTP responses, the deletion anchor and rendered pages.

Do not deploy the six-file subset directly to production: Direct Upload replaces the deployment manifest. Previous deployment ID, source checksums and verification belong in `docs/app-store/website-publication.json`. Global headers and redirect source snapshots for this publication are recorded there/alongside the publication evidence; no authentication material belongs in Git.
