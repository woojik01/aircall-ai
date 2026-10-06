#!/usr/bin/env python3
"""Render the public policy and its linked pages from one release policy source."""
import argparse
from pathlib import Path
import shutil

from check_play_config import load_config, validate, render_policy


def prepare(config, output):
    validate(config)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    render_policy(config, 'docs/play/privacy-policy.template.html', output / 'privacy-policy.html')
    for name in ('index', 'support'):
        render_policy(config, f'docs/public/{name}.template.html', output / f'{name}.html')
    shutil.copyfile('docs/public/site.css', output / 'site.css')
    (output / '.nojekyll').write_text('', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('dist/public'))
    args = parser.parse_args()
    prepare(validate(load_config('config/play.properties')), args.output)
    print('Public policy, support and stylesheet prepared; no publication performed')


if __name__ == '__main__':
    main()
