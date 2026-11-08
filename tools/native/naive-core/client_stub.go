//go:build !with_naive

package naivecore

import (
	"context"
	"errors"
)

func newNativeClient(context.Context, Config) (Client, error) {
	return nil, errors.New("native Naive build required")
}
