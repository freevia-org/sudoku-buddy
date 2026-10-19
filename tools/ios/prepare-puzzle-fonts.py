#!/usr/bin/env python3
"""Generate fixed puzzle faces from the Android reference Roboto variable font.

Requires fonttools; --source is the AOSP/Android Roboto-Regular.ttf documented in
Fonts/provenance.json. Existing generated faces are committed build inputs.
"""
import argparse
import hashlib
import json
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source',type=Path,required=True)
a=p.parse_args()
source_hash=hashlib.sha256(a.source.read_bytes()).hexdigest()
out=ROOT/'iosApp/SudokuBuddy/Fonts';out.mkdir(parents=True,exist_ok=True)
faces=[]
for name,weight,italic in [('Regular',400,0),('Medium',500,0),('Bold',700,0),('BoldItalic',700,1)]:
    font=instantiateVariableFont(TTFont(a.source),{'wght':weight,'wdth':100,'ital':italic},updateFontNames=True)
    font.recalcTimestamp=False
    path=out/f'Roboto-{name}.ttf';font.save(path)
    faces.append(dict(file=path.name,weight=weight,italic=bool(italic),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
metadata=dict(source='AOSP Roboto-Regular.ttf, Android API 36 reference image',source_sha256=source_hash,
              upstream='https://android.googlesource.com/platform/external/roboto-fonts/',
              copyright='Copyright 2011 Google Inc. All Rights Reserved.',license='Apache-2.0',faces=faces)
(out/'provenance.json').write_text(json.dumps(metadata,indent=2)+'\n')
