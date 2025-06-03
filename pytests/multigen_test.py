#!/usr/bin/env python3

#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import os
import re
import tempfile
import pytest

pytest.register_assert_rewrite('testlib')
pytest.register_assert_rewrite('apprunner')

import testlib
import apprunner

def run_multigen(command, **args):
    dir = tempfile.mkdtemp()
    print('temp dir:', dir)
    assert os.listdir(dir) == []
    testlib.check_command(command % dir, **args)
    return dir

def test__run_simple__generate():
    dir = run_multigen('multigen.sh -d work/testproj/src -o %s work/testproj/config/run-simple.xml')

    files = os.listdir(dir)
    assert 'node-config.properties' in files
    assert 'private.properties' in files
    assert 'blockchains' in files

    assert os.listdir(dir + '/blockchains') == ['1']

    files = sorted(os.listdir(dir + '/blockchains/1'))
    assert files == ['0.gtv', '0.xml', 'brid.txt']

    with open(dir + '/blockchains/1/brid.txt', 'rt') as f:
        brid = f.read()
    assert re.fullmatch(r'[0-9A-F]{64}', brid), brid

def test__runxml_docs_sample():
    dir = run_multigen('multigen.sh -d work/runxml-docs-sample/src -o %s work/runxml-docs-sample/run.xml')

    files = os.listdir(dir)
    assert 'node-config.properties' in files
    assert 'private.properties' in files
    assert 'blockchains' in files

    assert sorted(os.listdir(dir + '/blockchains')) == ['1', '2']
    assert sorted(os.listdir(dir + '/blockchains/1')) == ['0.gtv', '0.xml', '1000.gtv', '1000.xml', 'brid.txt']
    assert sorted(os.listdir(dir + '/blockchains/2')) == ['0.gtv', '0.xml', '1000.gtv', '1000.xml', '2000.gtv', '2000.xml', '3000.gtv', '3000.xml', 'brid.txt']

    with open(dir + '/blockchains/1/brid.txt', 'rt') as f:
        brid = f.read()
    assert re.fullmatch(r'[0-9A-F]{64}', brid), brid

    with open(dir + '/blockchains/2/brid.txt', 'rt') as f:
        brid = f.read()
    assert re.fullmatch(r'[0-9A-F]{64}', brid), brid

def test__modargs_ok():
    run_multigen('multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-ok.xml')

def test__modargs_missing():
    run_multigen('multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-missing.xml', code = 2,
        stderr = 'ERROR: Missing module_args for module(s): modargs.bar\n')

def test__modargs_wrong():
    run_multigen('multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-wrong.xml', code = 2,
        stderr = "ERROR: Bad module_args for module 'modargs.bar': Decoding type 'text': expected STRING, actual INTEGER (attribute: modargs.bar:module_args.a)\n")

def test__modargs_extra():
    run_multigen('multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-extra.xml')
