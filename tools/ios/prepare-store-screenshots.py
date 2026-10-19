#!/usr/bin/env python3
"""Select actual portrait XCTest captures and losslessly export opaque RGB PNGs.

Requires Pillow. This changes encoding only: no retouching, cropping or simulated camera image.
"""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image


def prepare(family, source, output):
    records = json.loads((source / 'manifest.json').read_text())
    selections = [
        ('testConfidentPhotoOpensAsPuzzleWithoutForcedReview()', 'Confident photograph - Android normal entry', '01-puzzle', 'An imported paper Sudoku, ready to check or ask for help.'),
        ('testPhotographedFourStageHint()', 'Hint 1 - Where', '02-hint-where', 'The first hint stage identifies the next square.'),
        ('testPhotographedFourStageHint()', 'Hint 2 - Square candidates', '03-hint-square', 'Explore the starting candidates for the selected square.'),
        ('testPhotographedFourStageHint()', 'Hint 3 - Trace', '04-hint-trace', 'Trace why candidates can be ruled out.'),
        ('testPhotographedFourStageHint()', 'Photographed hint - Answer', '05-hint-answer', 'Reveal the answer and revisit the placement evidence.'),
    ]
    result=[]
    for test, label, name, description in selections:
        matches=[a for record in records if record.get('testIdentifier','').endswith('/'+test)
                 for a in record.get('attachments',[]) if a.get('suggestedHumanReadableName','').startswith(label+'_')]
        assert len(matches)==1,(family,test,label,len(matches))
        item=matches[0]; original=source/item['exportedFileName']
        result.append(export(family,original,output/(family.lower()+'-'+name+'.png'),description,
                             {'test':test,'attachment':item['exportedFileName'],'timestamp':item['timestamp']}))
    return result


def export(family, original, target, description, provenance):
    with Image.open(original) as source:
        assert source.getexif().get(274,1)==1,'Only upright portrait captures are selected'
        rgba=source.convert('RGBA')
        assert rgba.getchannel('A').getextrema()==(255,255)
        rgb=source.convert('RGB');before=rgb.tobytes();rgb.info.clear();rgb.save(target)
    with Image.open(target) as check:
        assert check.mode=='RGB' and check.tobytes()==before,'Rendered RGB pixels must remain unchanged'
        size=list(check.size)
    return {'family':family,'file':target.name,'pixels':size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
            'description':description,'source':str(original),'provenance':provenance,'rgb_pixels_unchanged':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--iphone',type=Path,required=True)
    parser.add_argument('--ipad',type=Path,required=True)
    parser.add_argument('--manual-dir',type=Path,help='Optional directory containing iphone/ipad-store-read/history.png captures')
    parser.add_argument('--output',type=Path,default=Path('docs/app-store/screenshots'))
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    items=prepare('iPhone',args.iphone,args.output)+prepare('iPad',args.ipad,args.output)
    if args.manual_dir:
        for family in ['iPhone','iPad']:
            for action in ['read','history']:
                original=args.manual_dir/(family.lower()+'-store-'+action+'.png')
                name=family.lower()+('-06-read.png' if action=='read' else '-07-history.png')
                description='Review the recognized clues and handwriting.' if action=='read' else 'Reopen saved puzzle photos and progress in History.'
                items.append(export(family,original,args.output/name,description,{'capture':'simctl screenshot after native UI interaction','normal_launch':family=='iPhone'}))
    manifest={'captured':'2026-10-19','app_version':'1.0.1','build':'1','scope':'Actual simulator UI with a synthetic puzzle imported by the real reader; no private corpus imagery, mock camera view or retouching.',
              'devices':{'iPhone':'iPhone 17 Pro / iOS 27.0','iPad':'iPad Pro 13-inch (M5) / iPadOS 27.0'},'screenshots':items}
    (args.output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Exported',len(items),'screenshots with unchanged RGB pixels.')
