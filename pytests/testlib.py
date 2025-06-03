#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import json
import os
import re
import subprocess

TIMEOUT = 60

RELL_REPO_DIR = os.path.abspath('..')

def __get_rell_scripts_dir():
    k = 'RELL_SCRIPTS_DIR'
    m = os.environ
    return m[k] if k in m else os.path.join(RELL_REPO_DIR, 'work')

RELL_SCRIPTS_DIR=__get_rell_scripts_dir()

class VerInfo:
    def __init__(self):
        pom_path = os.path.join(RELL_REPO_DIR, 'pom.xml')
        with open(pom_path, 'rt') as f:
            lines = [l.strip() for l in f]
        self.rell_version = VerInfo.__find_pattern(lines, r'<version>(.+)</version>')
        self.postchain_version = VerInfo.__find_pattern(lines, r'<postchain.version>(.+)</postchain.version>')

    def __find_pattern(lines, regex_str):
        for line in lines:
            m = re.fullmatch(regex_str, line)
            if m:
                return m.group(1)
        return None

__VER_INFO = VerInfo()

RELL_MAVEN_VERSION = __VER_INFO.rell_version # TODO discover from sources or binaries somehow (pom? file name?)
POSTCHAIN_MAVEN_VERSION = __VER_INFO.postchain_version # TODO same

RELL_VERSION = (lambda s: s if '-' not in s else s[:s.index('-')])(RELL_MAVEN_VERSION)

MSG_META_TABLE_DOES_NOT_EXIST = '<LOG:INFO>SQLDatabaseAccess - Meta table does not exist. Assume database does not exist and create it (version: 2).'

STDOUT_IGNORE = [
    '<LOG:INFO><RE>SQLDatabaseAccess - Upgrading to version [0-9]+',
    '<LOG:INFO><RE>SQLDatabaseAccess - Database version has been updated to version: [0-9]+',
    '<LOG:INFO><RE>FluentPropertyBeanIntrospector - Error when creating PropertyDescriptor .*',
]

class LogFormat:
    __LOG_TIME_PATTERN = '####-##-## ##:##:##.###'
    __LOG_TIME_REGEX_STR = __LOG_TIME_PATTERN.replace('#', '[0-9]').replace('.', '[.]')

    def __init__(self, format):
        self.__format = format
        self.__format % {'time':'2022-02-22', 'level':'INFO', 'message':'Test'} #Test

    def make_regex(self, level, message_regex_str):
        return self.__format % {
            'time': LogFormat.__LOG_TIME_REGEX_STR,
            'level': level,
            'message': message_regex_str
        }

LOG_FORMAT_RELL = LogFormat(r'%(time)s %(level)-5s %(message)s')
LOG_FORMAT_POSTCHAIN = LogFormat(r'%(level)-5s %(time)s \[[^\]]+\] %(message)s')

class LineMatcher:
    def match(self, actual):
        return False

class ExactLineMatcher(LineMatcher):
    def __init__(self, expected):
        self.__expected = expected

    def match(self, actual):
        return actual == self.__expected

    def __repr__(self):
        return self.__expected

class RegexLineMatcher(LineMatcher):
    def __init__(self, expected_regex):
        self.__expected = expected_regex

    def match(self, actual):
        return self.__expected.fullmatch(actual)

    def __repr__(self):
        return str(self.__expected)

REGEX_TYPE = type(re.compile(r'.'))

class LineMatcherFactory:
    __LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR']
    __LOG_PREFIXES = [('<LOG:%s>' % s, s) for s in __LOG_LEVELS]
    __REGEX_PREFIX = '<RE>'
    __TEST_PREFIX = '<TEST>'

    def __init__(self, log_format):
        self.__log_format = log_format

    def matcher(self, expected):
        if type(expected) == REGEX_TYPE:
            return RegexLineMatcher(expected)

        log_level = None
        for prefix, level in LineMatcherFactory.__LOG_PREFIXES:
            has_prefix, expected = LineMatcherFactory.__remove_prefix(expected, prefix)
            if has_prefix:
                log_level = level
                break

        is_test, expected = LineMatcherFactory.__remove_prefix(expected, LineMatcherFactory.__TEST_PREFIX)
        is_regex, expected = LineMatcherFactory.__remove_prefix(expected, LineMatcherFactory.__REGEX_PREFIX)

        if not is_regex:
            expected = re.escape(expected)
        if is_test:
            expected = expected + r' \([0-9]+(\.[0-9]+)?s\)'
        if log_level != None:
            expected = self.__log_format.make_regex(log_level, expected)

        return RegexLineMatcher(re.compile(expected))

    def __remove_prefix(s, prefix):
        if s.startswith(prefix):
            return True, s[len(prefix):]
        else:
            return False, s

def check_json(actual_str, expected):
    actual = json.loads(actual_str)

    if type(expected) == str:
        expected = json.loads(expected)

    def check_recursive(act, exp):
        exp_type = type(exp)
        if exp_type == dict:
            assert type(act) == dict
            for k, v in exp.items():
                assert k in act
                check_recursive(act[k], v)
        elif exp_type == list:
            assert type(act) == list
            assert len(act) == len(exp)
            for i in range(len(exp)):
                check_recursive(act[i], exp[i])
        elif exp_type == str or exp_type == REGEX_TYPE:
            assert type(act) == str
            matcher = LineMatcherFactory(LOG_FORMAT_RELL).matcher(exp)
            assert matcher.match(act), (exp, act)
        else:
            assert act == exp

    check_recursive(actual, expected)

def check_output(actual, expected, ignored = [], log_format = LOG_FORMAT_RELL):
    matcher_factory = LineMatcherFactory(log_format)

    if type(actual) == list:
        act_list = actual
    else:
        assert actual == '' or actual.endswith('\n')
        act_list = actual[:-1].split('\n')

    ignored_matchers = [matcher_factory.matcher(s) for s in ignored]
    act_list = [s for s in act_list if not [m for m in ignored_matchers if m.match(s)]]

    if type(expected) == str:
        if ignored:
            actual = '\n'.join(act_list)
        assert actual == expected
        return

    assert type(expected) == list
    exp_list = expected

    for exp_line in exp_list:
        assert act_list, exp_line
        act_line = act_list.pop(0)
        matcher = matcher_factory.matcher(exp_line)
        #print(matcher)
        if not matcher.match(act_line):
            assert act_line == exp_line
            assert 0 # just in case

    assert act_list == []

def split_command(command):
    parts = command.split()
    if '/' not in parts[0]:
        parts[0] = os.path.join(RELL_SCRIPTS_DIR, parts[0])
    return parts

def run_command(command):
    parts = split_command(command)
    r = subprocess.run(parts, cwd = RELL_REPO_DIR, stdout = subprocess.PIPE, stderr = subprocess.PIPE, timeout = TIMEOUT, universal_newlines = True)
    return r

def check_command(command, stdout = '', stderr = '', stdout_ignore = [], stderr_ignore = [], code = 0, log_format = LOG_FORMAT_RELL):
    r = run_command(command)
    assert r.returncode == code
    check_output(r.stdout, stdout, stdout_ignore, log_format = log_format)
    check_output(r.stderr, stderr, stderr_ignore, log_format = log_format)

def check_tests(command, code, expected):
    r = run_command(command)
    assert r.returncode == code
    assert r.stderr == ''

    assert type(expected) == list
    exp_list = expected

    act_text = r.stdout
    assert act_text.endswith('\n')
    act_list = act_text[:-1].split('\n')

    i = act_list.index(exp_list[0])
    act_list = act_list[i:]
    check_output(act_list, exp_list)
