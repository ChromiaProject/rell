#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import xml.dom.minidom
import pytest

pytest.register_assert_rewrite('testlib')

import testlib

def check_rellcfg(command):
    p = testlib.run_command(command)
    assert p.returncode == 0
    assert p.stderr == ''
    assert p.stdout.startswith('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n')
    doc = xml.dom.minidom.parseString(p.stdout)
    assert doc.firstChild.nodeName == 'dict'

def test__stair():
    check_rellcfg('rellcfg.sh -d work/testproj/src stair')

def test__mod():
    check_rellcfg('rellcfg.sh -d work/testproj/src mod')

def test__mod_complex_foo():
    check_rellcfg('rellcfg.sh -d work/testproj/src mod.complex.foo')
