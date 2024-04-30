#  Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.

import re
import pytest

pytest.register_assert_rewrite('testlib')
pytest.register_assert_rewrite('apprunner')

import testlib
import apprunner

def check_intro(app):
    app.check_output([
        r'<RE>Rell %s' % re.escape(testlib.RELL_MAVEN_VERSION),
        'Type \'\\q\' to quit or \'\\?\' for help.',
        '>>> ',
    ])

def check_quit(app):
    app.input('\\q\n')
    app.check_output([])
    app.check_stop()

def test__none():
    app = apprunner.ReplRunner('rell.sh -d work/testproj/src')
    try:
        check_intro(app)
        check_quit(app)
    finally:
        app.stop()

def test__sum_digits_integer():
    app = apprunner.ReplRunner('rell.sh -d work/testproj/src')
    try:
        check_intro(app)
        app.input('import calc;\n')
        app.check_output(['>>> '])
        app.input('calc.sum_digits_integer(1000)\n')
        app.check_output([
            '73fb9a5de29b',
            '>>> '
        ])
        check_quit(app)
    finally:
        app.stop()

def test__company_user__no_tables():
    app = apprunner.ReplRunner('rell.sh --resetdb --db-properties work/testproj/config/node-config.properties -d work/testproj/src')
    try:
        app.ignore_output(testlib.MSG_META_TABLE_DOES_NOT_EXIST)
        app.ignore_output(*testlib.STDOUT_IGNORE)

        app.check_output([
            '<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)',
            '<LOG:INFO>SqlInit - Database init plan: 13 step(s)',
            '<LOG:INFO>SqlInit - Step: Create ROWID table and function',
            '<LOG:INFO>SqlInit - Step: Create meta tables',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_integer_power\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_biginteger_from_text\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_biginteger_power\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_decimal_from_text\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_decimal_to_text\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_bytea_substr1\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_bytea_substr2\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_text_repeat\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_text_substr1\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_text_substr2\'',
            '<LOG:INFO>SqlInit - Step: Create function: \'rell_text_getchar\'',
        ], ignore_rest = True)

        check_intro(app)

        app.input('import c: repl.company;\n')
        app.check_output(['>>> '])

        app.input('\\od\n')
        app.check_output(['>>> '])

        app.input('\\db-update\n')
        app.check_output([
            '<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)',
            '<LOG:INFO>SqlInit - Database init plan: 1 step(s)',
            '<LOG:INFO>SqlInit - Step: Create table and meta for \'repl.company:company\' (meta: company)',
            '>>> ',
        ])

        app.input('import u: repl.user;\n')
        app.check_output(['>>> '])

        app.input('c.company @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        app.input('u.user @* {}\n')
        app.check_output([
            'Run-time error: SQL Error: ERROR: relation "c0.user" does not exist',
            '  Position: 25',
            '<RE>  Location: File: parse_relation.c, Routine: parserOpenTable, Line: [0-9]+',
            '  Server SQLState: 42P01',
            '	at :<console>(<console>:1)',
            '>>> ',
        ])

        app.input('c.company @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        app.input('\\db-update\n')
        app.check_output([
            '<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)',
            '<LOG:INFO>SqlInit - Database init plan: 1 step(s)',
            '<LOG:INFO>SqlInit - Step: Create table and meta for \'repl.user:user\' (meta: user)',
            '>>> '
        ])

        app.input('u.user @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        app.input('c.company @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        check_quit(app)
    finally:
        app.stop()

def test__company_user__existing_tables():
    app = apprunner.ReplRunner('rell.sh --resetdb --db-properties work/testproj/config/node-config.properties -d work/testproj/src')
    try:
        app.ignore_output(testlib.MSG_META_TABLE_DOES_NOT_EXIST)
        app.ignore_output('<LOG:INFO><RE>SqlInit - .+')
        app.ignore_output(*testlib.STDOUT_IGNORE)
        check_intro(app)
        app.input('import repl.company; import repl.user;\n')
        app.check_output(['>>> '])
        app.input('\\db-update\n')
        app.check_output(['>>> '])
        check_quit(app)
    finally:
        app.stop()

    app = apprunner.ReplRunner('rell.sh --db-properties work/testproj/config/node-config.properties -d work/testproj/src')
    try:
        app.ignore_output(testlib.MSG_META_TABLE_DOES_NOT_EXIST)
        app.ignore_output(*testlib.STDOUT_IGNORE)
        app.check_output([
            '<LOG:INFO>SqlInit - Initializing database (chain_iid = 0)',
            '<LOG:WARN>SqlInit - Table for undefined entity \'company\' found',
            '<LOG:WARN>SqlInit - Table for undefined entity \'user\' found',
        ], ignore_rest = True)
        check_intro(app)

        app.input('\\od\n')
        app.check_output(['>>> '])

        app.input('import c: repl.company;\n')
        app.check_output(['>>> '])

        app.input('c.company @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        app.input('import u: repl.user;\n')
        app.check_output(['>>> '])

        app.input('u.user @* {}\n')
        app.check_output([
            '[]',
            '>>> '
        ])

        check_quit(app)
    finally:
        app.stop()

def init_output_format(app):
    check_intro(app)
    app.input("val l = [(123,'Hello'),(456,'Bye'),(789,'Ciao')];\n")
    app.check_output(['>>> '])
    app.input("val m = [123:'Hello',456:'Bye',789:'Ciao'];\n")
    app.check_output(['>>> '])
    app.input("val m2 = ['Hello':123,'Bye':456,'Ciao':789];\n")
    app.check_output(['>>> '])

def test__output_format__od():
    app = apprunner.ReplRunner('rell.sh')
    try:
        init_output_format(app)
        app.input('\\od\n')
        app.check_output(['>>> '])
        app.input('l\n')
        app.check_output(['[(123,Hello), (456,Bye), (789,Ciao)]', '>>> '])
        app.input('m\n')
        app.check_output(['{123=Hello, 456=Bye, 789=Ciao}', '>>> '])
        app.input('m2\n')
        app.check_output(['{Hello=123, Bye=456, Ciao=789}', '>>> '])
        check_quit(app)
    finally:
        app.stop()

def test__output_format__ol():
    app = apprunner.ReplRunner('rell.sh')
    try:
        init_output_format(app)
        app.input('\\ol\n')
        app.check_output(['>>> '])
        app.input('l\n')
        app.check_output(['(123,Hello)', '(456,Bye)', '(789,Ciao)', '>>> '])
        app.input('m\n')
        app.check_output(['123=Hello', '456=Bye', '789=Ciao', '>>> '])
        app.input('m2\n')
        app.check_output(['Hello=123', 'Bye=456', 'Ciao=789', '>>> '])
        check_quit(app)
    finally:
        app.stop()

def test__output_format__og():
    app = apprunner.ReplRunner('rell.sh')
    try:
        init_output_format(app)
        app.input('\\og\n')
        app.check_output(['>>> '])

        app.input('123\n')
        app.check_output(['123', '>>> '])
        app.input('123L\n')
        app.check_output(['123L', '>>> '])
        app.input('123.0\n')
        app.check_output(['"123"', '>>> '])
        app.input('true\n')
        app.check_output(['1', '>>> '])
        app.input('x"1234"\n')
        app.check_output(['x"1234"', '>>> '])
        app.input('"Hello"\n')
        app.check_output(['"Hello"', '>>> '])

        app.input('l\n')
        app.check_output(['[[123, "Hello"], [456, "Bye"], [789, "Ciao"]]', '>>> '])
        app.input('m\n')
        app.check_output(['[[123, "Hello"], [456, "Bye"], [789, "Ciao"]]', '>>> '])
        app.input('m2\n')
        app.check_output(['["Bye": 456, "Ciao": 789, "Hello": 123]', '>>> '])
        check_quit(app)
    finally:
        app.stop()

def test__output_format__oj():
    app = apprunner.ReplRunner('rell.sh')
    try:
        init_output_format(app)
        app.input('\\oj\n')
        app.check_output(['>>> '])
        app.input('l\n')
        app.check_output([
            '[',
            '  [',
            '    123,',
            '    "Hello"',
            '  ],',
            '  [',
            '    456,',
            '    "Bye"',
            '  ],',
            '  [',
            '    789,',
            '    "Ciao"',
            '  ]',
            ']',
            '>>> '
        ])
        app.input('m\n')
        app.check_output([
            '[',
            '  [',
            '    123,',
            '    "Hello"',
            '  ],',
            '  [',
            '    456,',
            '    "Bye"',
            '  ],',
            '  [',
            '    789,',
            '    "Ciao"',
            '  ]',
            ']',
            '>>> ',
        ])
        app.input('m2\n')
        app.check_output([
            '{',
            '  "Bye": 456,',
            '  "Ciao": 789,',
            '  "Hello": 123',
            '}',
            '>>> '
        ])

        check_quit(app)
    finally:
        app.stop()

def test__output_format__ox():
    app = apprunner.ReplRunner('rell.sh')
    try:
        init_output_format(app)
        app.input('\\ox\n')
        app.check_output(['>>> '])
        app.input('l\n')
        app.check_output([
            '<array>',
            '    <array>',
            '        <int>123</int>',
            '        <string>Hello</string>',
            '    </array>',
            '    <array>',
            '        <int>456</int>',
            '        <string>Bye</string>',
            '    </array>',
            '    <array>',
            '        <int>789</int>',
            '        <string>Ciao</string>',
            '    </array>',
            '</array>',
            '>>> ',
        ])
        app.input('m\n')
        app.check_output([
            '<array>',
            '    <array>',
            '        <int>123</int>',
            '        <string>Hello</string>',
            '    </array>',
            '    <array>',
            '        <int>456</int>',
            '        <string>Bye</string>',
            '    </array>',
            '    <array>',
            '        <int>789</int>',
            '        <string>Ciao</string>',
            '    </array>',
            '</array>',
            '>>> ',
        ])
        app.input('m2\n')
        app.check_output([
            '<dict>',
            '    <entry key="Bye">',
            '        <int>456</int>',
            '    </entry>',
            '    <entry key="Ciao">',
            '        <int>789</int>',
            '    </entry>',
            '    <entry key="Hello">',
            '        <int>123</int>',
            '    </entry>',
            '</dict>',
            '>>> ',
        ])

        check_quit(app)
    finally:
        app.stop()
