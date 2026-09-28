package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"

	"nimbo/awgcore"
)

func run() error {
	if len(os.Args) == 2 && os.Args[1] == "--version" {
		fmt.Println(awgcore.Version)
		return nil
	}
	if len(os.Args) != 1 {
		return fmt.Errorf("usage: nimbo-awg (configuration via stdin) or --version")
	}
	reader := bufio.NewReader(io.LimitReader(os.Stdin, awgcore.MaxConfigBytes*2))
	line, e := reader.ReadBytes('\n')
	if e != nil {
		return fmt.Errorf("configuration line required")
	}
	var request struct {
		Config   string `json:"config"`
		Listen   string `json:"listen"`
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if e = json.Unmarshal(line, &request); e != nil {
		return fmt.Errorf("invalid startup request")
	}
	runtime, e := awgcore.Start(request.Config, request.Listen, request.Username, request.Password)
	if e != nil {
		return e
	}
	defer runtime.Close()
	if e = json.NewEncoder(os.Stdout).Encode(map[string]any{"port": runtime.Port(), "version": awgcore.Version}); e != nil {
		return fmt.Errorf("could not report readiness")
	}
	done := make(chan struct{})
	go func() { _, _ = io.Copy(io.Discard, os.Stdin); close(done) }()
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(signals)
	select {
	case <-done:
	case <-signals:
	}
	return nil
}
func main() {
	if e := run(); e != nil {
		fmt.Fprintln(os.Stderr, e)
		os.Exit(1)
	}
}
