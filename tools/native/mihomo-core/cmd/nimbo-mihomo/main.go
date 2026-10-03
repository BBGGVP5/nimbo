package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"
	"time"
	"unicode/utf8"

	"github.com/sirupsen/logrus"
	core "nimbo/mihomocore"
)

func main() { os.Exit(run()) }
func run() int {
	logrus.SetOutput(os.Stderr)
	if len(os.Args) == 2 && os.Args[1] == "serve-tun" {
		signals := make(chan os.Signal, 1)
		signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
		defer signal.Stop(signals)
		return runTun(os.Stdin, os.Stdout, signals, core.StartDesktopTun, core.Invoke)
	}
	if len(os.Args) != 2 || (os.Args[1] != "inspect" && os.Args[1] != "validate-tun" && os.Args[1] != "serve") {
		fmt.Fprintln(os.Stderr, "usage: nimbo-mihomo inspect|validate-tun|serve (stdin to EOF) | serve-tun (framed privileged startup + stdin lease)")
		return 2
	}
	b, err := io.ReadAll(io.LimitReader(os.Stdin, (8<<20)+1))
	if err != nil {
		fmt.Fprintln(os.Stderr, "stdin read failed")
		return 1
	}
	var request string
	if os.Args[1] == "inspect" || os.Args[1] == "validate-tun" {
		if !utf8.Valid(b) {
			fmt.Fprintln(os.Stderr, "invalid UTF-8 YAML")
			return 1
		}
		operation := "inspect"
		if os.Args[1] == "validate-tun" {
			operation = "preflightDesktopTun"
		}
		msg, _ := json.Marshal(map[string]any{"apiVersion": 1, "requestId": "cli-inspect", "operation": operation, "yaml": string(b)})
		request = string(msg)
	} else {
		var header struct {
			Operation string `json:"operation"`
		}
		if json.Unmarshal(b, &header) != nil || header.Operation != "start" {
			fmt.Fprintln(os.Stderr, "serve requires a start request")
			return 2
		}
		request = string(b)
	}
	result := core.Invoke(request)
	fmt.Println(result)
	var reply struct {
		Success bool `json:"success"`
	}
	_ = json.Unmarshal([]byte(result), &reply)
	if !reply.Success {
		return 1
	}
	if os.Args[1] == "inspect" || os.Args[1] == "validate-tun" {
		return 0
	}
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(signals)
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-signals:
			core.Invoke(`{"apiVersion":1,"operation":"stop"}`)
			return 0
		case <-ticker.C:
			var s struct {
				Data struct {
					State string `json:"state"`
				} `json:"data"`
			}
			_ = json.Unmarshal([]byte(core.Invoke(`{"apiVersion":1,"operation":"status"}`)), &s)
			if s.Data.State == "stopped" {
				time.Sleep(100 * time.Millisecond)
				return 0
			}
		}
	}
}
