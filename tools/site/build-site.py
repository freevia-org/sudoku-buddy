#!/usr/bin/env python3
"""Build only Sudoku-owned Pages paths. Requires Python-Markdown (pinned in requirements.txt)."""
import argparse,hashlib,html,re
from pathlib import Path
import markdown

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent


def render(text):
    rendered=markdown.markdown(text,extensions=['tables','toc'])
    return re.sub(r'href="(?!(?:https?:|mailto:|/|#))([^"]+)"',lambda m:'href="https://github.com/freevia-org/sudoku-buddy/blob/main/docs/'+m[1]+'"',rendered)


def build():
    files={}
    policy=(ROOT/'docs/privacy-policy.md').read_text()
    body=policy[policy.index('Sudoku Buddy is published'):]
    template=(HERE/'templates/policy.html').read_text()
    body_html='<!-- Policy source SHA-256 '+hashlib.sha256(policy.encode()).hexdigest()+' -->\n'+render(body)
    files['sudoku-buddy/privacy/index.html']=template.replace('<!-- CONTENT -->',body_html).encode()
    support='''## Sudoku Buddy support

Android is available on Google Play. The iPhone and iPad version is in preparation and is not yet available on the App Store.

For private help, email [info@freevia.org](mailto:info@freevia.org). Public [GitHub issues](https://github.com/freevia-org/sudoku-buddy/issues) and attachments can be seen by anyone; do not include private photographs or personal information.

## Android

Use the [usage guide](https://github.com/freevia-org/sudoku-buddy/blob/main/docs/user-guide.md) for scanning, correcting readings, checking answers, hints and saved history. Keep the whole grid visible, use even light, and check the recognized clues and handwriting before trusting a result.

## iPhone and iPad preview

'''+(ROOT/'docs/ios-user-guide.md').read_text().split('\n',2)[2]+'''

## Reporting a problem

Include the app version, iOS/iPadOS or Android version, device model, steps to reproduce, what you expected, and what happened. Tell us whether you checked and corrected the recognized reading. Use email for private examples. Report submission is optional; no support account inside Sudoku Buddy is required.

## Delete your data

Delete saved puzzles and refused scans from History. To delete a previously submitted report, use Settings > Submission receipts and delete each revision. Deleting a local puzzle does not delete an uploaded report. See the [privacy policy](/sudoku-buddy/privacy/#deleting-your-data).
'''
    support_template=template.replace('How Sudoku Buddy keeps puzzles on your phone and handles optional private recognition reports, training examples and deletion receipts.','Sudoku Buddy support for Android and the iPhone/iPad preview: scanning, saved puzzles, reporting and deletion.').replace('Sudoku Buddy Privacy Policy — Freevia','Sudoku Buddy Support — Freevia').replace('https://freevia.org/sudoku-buddy/privacy/','https://freevia.org/sudoku-buddy/support/').replace('Your puzzle, your choice.','A little help, when you need it.').replace('<div class="eyebrow">Privacy policy</div>','<div class="eyebrow">Support</div>').replace('Scanning and solving happen on your phone. This policy explains optional private reports, how submitted examples are used, and how to delete them by receipt.','Scanning tips, photo import, saved puzzles and optional feedback on Android and the iOS preview.')
    files['sudoku-buddy/support/index.html']=support_template.replace('<!-- CONTENT -->',render(support)).encode()
    gallery='''<section class="screens-section" id="ios-preview" aria-labelledby="ios-title"><div class="shell"><div class="section-heading"><div><span class="eyebrow">iPhone and iPad · in preparation</span><h2 id="ios-title">The same puzzle companion, coming to iOS.</h2></div><p>Actual iPhone simulator previews. The iOS version is not yet available on the App Store.</p></div><div class="screenshot-grid">'''
    for filename,title,description in [
        ('iphone-01-puzzle.png','Keep your paper puzzle','Import a photograph and review the recognized grid.'),
        ('iphone-03-hint-square.png','Explore four hint stages','Where, Square, Trace and Answer reveal help at your pace.'),
        ('iphone-05-hint-answer.png','Understand the next move','Revisit the placement evidence and trace the reasoning.')]:
        target='assets/sudoku-buddy/ios-'+filename
        files[target]=(ROOT/'docs/app-store/screenshots'/filename).read_bytes()
        gallery+='<figure class="screen-card"><div class="screen-window"><img src="/'+target+'" alt="'+html.escape(title+' — '+description)+'" loading="lazy"></div><figcaption><strong>'+title+'</strong><span>'+description+'</span></figcaption></figure>'
    gallery+='</div><p class="screen-note">Recognition, checking, solving and tutoring run on the device. Reports are optional and can be deleted by receipt. <a href="/sudoku-buddy/support/">Read the support guide</a>.</p></div></section>'
    files['sudoku-buddy/index.html']=(HERE/'templates/product.html').read_text().replace('<!-- IOS_SCREENSHOTS -->',gallery).encode()
    return files


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
    for name,data in build().items():
        dest=ROOT/'docs/site'/name
        if args.check: assert dest.read_bytes()==data,name+' is stale'
        else: dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    print('Verified' if args.check else 'Built','six Sudoku-owned website assets.')
