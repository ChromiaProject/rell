#  Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.

import re
import pytest

pytest.register_assert_rewrite('testlib')

import testlib

run_command = testlib.run_command
check_command = testlib.check_command

def test__calc__sum_digits_integer():
    check_command('rell.sh -d work/testproj/src calc sum_digits_integer 1000', '73fb9a5de29b\n')

def test__calc__sum_digits_decimal():
    check_command('rell.sh -d work/testproj/src calc sum_digits_decimal 1000', '73fb9a5de29b\n')

def test__stair__stairq():
    check_command('rell.sh -d work/testproj/src stair stairq 10', '0\n 1\n  2\n   3\n    4\n     5\n      6\n       7\n        8\n         9\n45\n')

def test__stair__stairf():
    check_command('rell.sh -d work/testproj/src stair stairf 10', '0\n 1\n  2\n   3\n    4\n     5\n      6\n       7\n        8\n         9\n45\n')

def test__misc__ns_fun():
    check_command('rell.sh -d work/testproj/src misc ns.f', [
        'Inside function fun!',
        '<LOG:INFO>Rell - [misc:ns.f(misc.rell:6)] Some logging',
    ])

def test__mod__main():
    check_command('rell.sh -d work/testproj/src mod main', 'This is main!\nAnd this is helper!\nHello from support!\n')

def test__mod_complex_foo__foo():
    check_command('rell.sh -d work/testproj/src mod.complex.foo foo', [
        '<LOG:INFO>Rell - [mod.complex.foo:foo(mod/complex/foo/foo.rell:4)] foo start',
        '<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:4)] bar start',
        '<LOG:INFO>Rell - [mod.complex.sub:helper(mod/complex/sub/helper.rell:2)] helper',
        '<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:6)] bar end',
        '<LOG:INFO>Rell - [mod.complex.foo:foo(mod/complex/foo/foo.rell:6)] foo end',
        '0',
    ])

def test__mod_complex_bar__bar():
    check_command('rell.sh -d work/testproj/src mod.complex.bar bar', [
        '<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:4)] bar start',
        '<LOG:INFO>Rell - [mod.complex.sub:helper(mod/complex/sub/helper.rell:2)] helper',
        '<LOG:INFO>Rell - [mod.complex.bar:bar(mod/complex/bar/bar.rell:6)] bar end',
    ])

def test__abstr_main__main():
    check_command('rell.sh -d work/testproj/src abstr.main main', 'f(123) = 15129\n')

def test__stack_trace__main__12345():
    check_command('rell.sh -d work/testproj/src stack_trace main 12345', code = 1,
    stdout = [
        '<LOG:INFO>Rell - [stack_trace:main(stack_trace/main.rell:24)] main start',
    ],
    stderr = [
        'ERROR x must be positive, but was 0',
        '\tat stack_trace:calc(stack_trace/main.rell:7)',
        '\tat stack_trace:calc(stack_trace/main.rell:11)',
        '\tat stack_trace:calc(stack_trace/main.rell:12)',
        '\tat stack_trace:calc(stack_trace/main.rell:13)',
        '\tat stack_trace:calc(stack_trace/main.rell:14)',
        '\tat stack_trace:calc(stack_trace/main.rell:15)',
        '\tat stack_trace:main(stack_trace/main.rell:25)',
    ])

def test__stack_trace__error__div():
    check_command('rell.sh -d work/testproj/src -- stack_trace error /', code = 1, stderr = [
        'ERROR Division by zero: 1 / 0',
        '\tat stack_trace:error(stack_trace/errors.rell:3)',
    ])

def test__stack_trace__error__mod():
    check_command('rell.sh -d work/testproj/src -- stack_trace error %', code = 1, stderr = [
        'ERROR Division by zero: 1 % 0',
        '\tat stack_trace:error(stack_trace/errors.rell:4)',
    ])

def test__stack_trace__error__minus():
    check_command('rell.sh -d work/testproj/src -- stack_trace error -', code = 1, stderr = [
        'ERROR Integer overflow: -(-9223372036854775808)',
        '\tat stack_trace:error(stack_trace/errors.rell:5)',
    ])

def test__stack_trace__error__abs():
    check_command('rell.sh -d work/testproj/src -- stack_trace error abs', code = 1, stderr = [
        'ERROR System function \'abs\': Integer overflow: -9223372036854775808',
        '\tat stack_trace:error(stack_trace/errors.rell:6)',
    ])

def test__stack_trace__entities__ent_main():
    check_command('rell.sh --resetdb --db-properties work/testproj/config/node-config.properties -d work/testproj/src -- stack_trace.entities ent_main', code = 1,
        stdout_ignore = testlib.STDOUT_IGNORE,
        stderr = [
            'ERROR No records found',
            '\tat stack_trace.entities:ent_main(stack_trace/entities.rell:6)',
        ]
    )

def test__calc__rell_get_rell_version():
    check_command('rell.sh -d work/testproj/src calc rell.get_rell_version', testlib.RELL_VERSION + '\n')

def test__calc__rell_get_postchain_version():
    check_command('rell.sh -d work/testproj/src calc rell.get_postchain_version', testlib.POSTCHAIN_MAVEN_VERSION + '\n')

def test__calc__rell_get_build():
    r = run_command('rell.sh -d work/testproj/src calc rell.get_build')
    assert r.returncode == 0
    assert r.stderr == ''

    parts = r.stdout.split('; ')
    assert re.fullmatch(r'rell: \d{1,3}[.]\d{1,3}[.]\d{1,3}(-SNAPSHOT)?', parts[0])
    assert re.fullmatch(r'postchain: \d{1,3}[.]\d{1,3}[.]\d{1,3}(-SNAPSHOT)?', parts[1])
    assert re.fullmatch(r'branch: .+', parts[2])
    assert re.fullmatch(r'commit: [0-9a-f]{5,} \(\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d[+]\d\d\d\d\)', parts[3])
    assert re.fullmatch(r'dirty: (false|true)\n', parts[4])

def test__calc__rell_get_build_details():
    r = run_command('rell.sh --json -d work/testproj/src calc rell.get_build_details')
    assert r.returncode == 0
    assert r.stderr == ''

    testlib.check_json(r.stdout, {
        'kotlin.version': r'<RE>\d{1,3}[.]\d{1,3}[.]\d{1,3}',
        'postchain.version': r'<RE>\d{1,3}[.]\d{1,3}[.]\d{1,3}(-SNAPSHOT)?',
        'rell.branch': r'<RE>.+',
        'rell.commit.id': r'<RE>[0-9a-f]{5,}',
        'rell.commit.id.full': r'<RE>[0-9a-f]{40}',
        'rell.commit.message.full': r'<RE>.+',
        'rell.commit.message.short': r'<RE>.+',
        'rell.commit.time': r'<RE>\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d[+]\d\d\d\d',
        'rell.dirty': r'<RE>false|true',
        'rell.version': r'<RE>\d{1,3}[.]\d{1,3}[.]\d{1,3}(-SNAPSHOT)?',
    })

def test__mod__rell_get_app_structure():
    r = run_command('rell.sh -d work/testproj/src mod rell.get_app_structure')
    assert r.returncode == 0
    assert r.stderr == ''

    testlib.check_json(r.stdout, '''
        {
            "modules": {
                "mod": {
                    "name": "mod",
                    "operations": {
                        "main": {
                            "mount": "main",
                            "parameters": []
                        }
                    }
                },
                "mod.sub.helper": {
                    "name": "mod.sub.helper",
                    "functions": {
                        "helper": {
                            "parameters": [],
                            "type": "unit"
                        }
                    },
                    "queries": {
                        "q": {
                            "mount": "q",
                            "parameters": [],
                            "type": "text"
                        }
                    }
                },
                "mod.sub.support": {
                    "name": "mod.sub.support",
                    "functions": {
                        "support": {
                            "parameters": [],
                            "type": "unit"
                        }
                    }
                }
            }
        }
    ''')

def test__app_structure__rell_get_app_structure():
    r = run_command('rell.sh -d work/testproj/src app_structure rell.get_app_structure')
    assert r.returncode == 0
    assert r.stderr == ''

    testlib.check_json(r.stdout, '''
        {
            "modules": {
                "app_structure": {
                    "name": "app_structure",
                    "entities": {
                        "company": {
                            "attributes": [
                                {
                                    "mutable": 0,
                                    "name": "name",
                                    "type": "text"
                                }
                            ],
                            "indexes": [],
                            "keys": [],
                            "log": 0,
                            "mount": "company"
                        },
                        "user": {
                            "attributes": [
                                {
                                    "mutable": 0,
                                    "name": "name",
                                    "type": "text"
                                },
                                {
                                    "mutable": 0,
                                    "name": "company",
                                    "type": "app_structure:company"
                                }
                            ],
                            "indexes": [],
                            "keys": [],
                            "log": 0,
                            "mount": "user"
                        }
                    },
                    "queries": {
                        "get_user_name": {
                            "mount": "get_user_name",
                            "parameters": [
                                {
                                    "name": "user",
                                    "type": "app_structure:user"
                                }
                            ],
                            "type": "text"
                        }
                    }
                }
            }
        }
    ''')

def test__tests_foobar():
    testlib.check_tests('rell.sh -d work/testproj/src tests.foobar', 1, [
        'TEST RESULTS:',
        '',
        '<TEST>OK tests.foobar:test_foo',
        '',
        '<TEST>FAILED tests.foobar:test_fail_require',
        '<TEST>FAILED tests.foobar:test_fail_assert_equals',
        '',
        '<TEST>SUMMARY: 2 FAILED / 1 PASSED / 3 TOTAL',
        '',
        '',
        '***** FAILED *****',
    ])

def test__tests_data_test():
    testlib.check_tests('rell.sh -d work/testproj/src --db-properties work/testproj/config/node-config.properties tests.data_test', 0, [
        'TEST RESULTS:',
        '',
        '<TEST>OK tests.data_test:test_add_user',
        '<TEST>OK tests.data_test:test_remove_user',
        '',
        '<TEST>SUMMARY: 0 FAILED / 2 PASSED / 2 TOTAL',
        '',
        '',
        '***** OK *****',
    ])

def test__test__tests():
    testlib.check_tests('rell.sh -d work/testproj/src --db-properties work/testproj/config/node-config.properties --test tests', 1, [
        'TEST RESULTS:',
        '',
        '<TEST>OK tests.calc_test:test_square',
        '<TEST>OK tests.calc_test:test_cube',
        '<TEST>OK tests.data_test:test_add_user',
        '<TEST>OK tests.data_test:test_remove_user',
        '<TEST>OK tests.event_test:test_event',
        '<TEST>OK tests.foobar:test_foo',
        '<TEST>OK tests.lib_test:test_lib',
        '',
        '<TEST>FAILED tests.foobar:test_fail_require',
        '<TEST>FAILED tests.foobar:test_fail_assert_equals',
        '',
        '<TEST>SUMMARY: 2 FAILED / 7 PASSED / 9 TOTAL',
        '',
        '',
        '***** FAILED *****',
    ])

def test__test__tests_calc_test__tests_data_test():
    testlib.check_tests('rell.sh -d work/testproj/src --db-properties work/testproj/config/node-config.properties --test tests.calc_test tests.data_test', 0, [
        'TEST RESULTS:',
        '',
        '<TEST>OK tests.calc_test:test_square',
        '<TEST>OK tests.calc_test:test_cube',
        '<TEST>OK tests.data_test:test_add_user',
        '<TEST>OK tests.data_test:test_remove_user',
        '',
        '<TEST>SUMMARY: 0 FAILED / 4 PASSED / 4 TOTAL',
        '',
        '',
        '***** OK *****',
    ])

def test__test__tests_event_test():
    testlib.check_tests('rell.sh -d work/testproj/src --db-properties work/testproj/config/node-config.properties --test tests.event_test', 0, [
        'TEST RESULTS:',
        '',
        '<TEST>OK tests.event_test:test_event',
        '',
        '<TEST>SUMMARY: 0 FAILED / 1 PASSED / 1 TOTAL',
        '',
        '',
        '***** OK *****',
    ])

def test__test__notfound():
    check_command('rell.sh -d work/testproj/src --test notfound', code = 1, stderr = 'ERROR: Module \'notfound\' not found\n')

def test__test__not_found():
    check_command('rell.sh -d work/testproj/src --test not.found', code = 1, stderr = 'ERROR: Module \'not.found\' not found\n')

def test__not_found():
    check_command('rell.sh -d work/testproj/src not_found', code = 1, stderr = 'ERROR: Module \'not_found\' not found\n')

def test__compilation_error():
    testlib.check_command('rell.sh -d work/testproj/src compilation_error', code = 1,
        stderr = 'compilation_error.rell(2:10) ERROR: Return type mismatch: text instead of integer\nErrors: 1 Warnings: 0\n'
    )
