#!/usr/bin/env python3

#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import os
import re
import subprocess
import time
import requests

import testlib

class AppRunner:
    def __init__(self, command, verbose, log_format):
        self.__verbose = verbose
        self.__matcher_factory = testlib.LineMatcherFactory(log_format)

        self.__process = subprocess.Popen(
            testlib.split_command(command),
            cwd = testlib.RELL_REPO_DIR,
            stdin = subprocess.PIPE,
            stdout = subprocess.PIPE,
            stderr = subprocess.PIPE,
            shell = False,
            universal_newlines = True
        )

        # https://stackoverflow.com/a/59291466
        # not the only option and not a portable one, but very simple
        os.set_blocking(self.__process.stdout.fileno(), False)

        self.__lines = []
        self.__ignored_lines = []

        self.api_port = None # Accessed by subclasses.

    def stop(self, wait = False):
        self.__process.terminate()
        if wait:
            self.__process.wait()

    def read_line(self):
        self.__fetch_lines()
        return None if not self.__lines else self.__lines.pop(0)

    def __fetch_lines(self):
        if self.__lines:
            return
        lines = self.__process.stdout.readlines()
        for line in lines:
            if line.endswith('\n'):
                line = line[:-1]
            if self.__verbose:
                print(line)
            self.__lines.append(line)

    def read_line_blocking(self, timeout = 10, skip_ignored = False):
        t = time.time()
        while True:
            line = self.read_line()
            if line != None:
                ignored = False
                if skip_ignored:
                    for ignore in self.__ignored_lines:
                        if ignore.match(line):
                            ignored = True
                            break
                if not ignored:
                    return line
            else:
                sleep = 0.1
                assert time.time() + sleep - t < timeout
                time.sleep(sleep)

    def input(self, text):
        self.__process.stdin.write(text)
        self.__process.stdin.flush()

    def ignore_output(self, *expected):
        for s in expected:
            matcher = self.__matcher_factory.matcher(s)
            self.__ignored_lines.append(matcher)

    def check_output(self, expected, ignore_rest = False):
        if type(expected) == str:
            expected = [expected]
        for exp in expected:
            act = self.read_line_blocking(skip_ignored = True)
            matcher = self.__matcher_factory.matcher(exp)
            assert matcher.match(act), (act, exp)
        if not ignore_rest:
            act = self.read_line()
            assert act == None, act

    def check_stop(self):
        t = time.time()
        sleep = 0.1
        while self.__process.poll() == None:
            assert time.time() + sleep - t < 10
            time.sleep(sleep)

    def get_return_code(self):
        return self.__process.poll()

class NodeRunner(AppRunner):
    def __init__(self, command, verbose, log_format):
        super().__init__(command, verbose, log_format)
        self.api_port = None # Accessed by subclasses.

    def read_until_line(self, regex):
        last_out_time = time.time()

        while True:
            code = self.get_return_code()
            assert code == None, 'terminated: %s' % code

            while True:
                line = self.read_line()
                if line == None:
                    break
                last_out_time = time.time()

                m = re.fullmatch(regex, line)
                if m:
                    return m.groups()

            if time.time() - last_out_time >= 20:
                self.stop(True)
                raise Exception('no output for a while')

            time.sleep(0.1)

    def send_post(self, path, data):
        port = self.api_port
        assert port != None
        url = 'http://127.0.0.1:%d/%s' % (port, path)
        return requests.post(url, data = data, timeout = 5)

    def skip_output(self):
        while self.read_line() != None:
            pass

    def check_query(self, req, status, text):
        r = self.send_post('query/iid_1', req)
        assert r.status_code == status
        assert r.text == text

class Multirun(NodeRunner):
    def __init__(self, command, verbose = False):
        super().__init__(command, verbose, log_format = testlib.LOG_FORMAT_RELL)
        self.__wait_till_up_called = False

    def wait_till_up(self):
        assert not self.__wait_till_up_called
        self.__wait_till_up_called = True
        self.read_until_line(r'.+ INFO  PostchainApp - POSTCHAIN APP STARTED')
        port_str, = self.read_until_line(r'.+ INFO  PostchainApp - [ ]*REST API port: (\d+)')
        self.api_port = int(port_str)

class ReplRunner(AppRunner):
    def __init__(self, command, verbose = False):
        super().__init__(command, verbose, log_format = testlib.LOG_FORMAT_RELL)

def main():
    app = Multirun('multirun.sh -d test-cli/src test-cli/config/run-simple.xml', verbose = True)
    try:
        app.wait_till_up()
        print('server is up!')

        r = app.send_post('query/iid_1', '{"type":"sum_digits_int","n":1000}')
        print('query response:', r)
        print(r.text)
    finally:
        app.stop()
        print('server stopped')

if __name__ == '__main__':
    main()
