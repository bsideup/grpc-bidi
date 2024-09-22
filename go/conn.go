package _go

import (
	"bytes"
	"context"
	"net"
	"time"
)

type closeFunc func() error

func (c closeFunc) Close() error {
	return c()
}

type anyStream interface {
	Context() context.Context

	SendMsg(m any) error

	RecvMsg(m any) error
}

type rawConn struct {
	stream anyStream
	closer func() error
}

var _ net.Conn = rawConn{}

func (c rawConn) SetReadDeadline(_ time.Time) error {
	return nil
}

func (c rawConn) SetWriteDeadline(_ time.Time) error {
	return nil
}

func (c rawConn) Read(p []byte) (n int, err error) {
	buf := bytes.NewBuffer(p)
	err = c.stream.RecvMsg(buf)

	return buf.Len(), err
}

func (c rawConn) Write(p []byte) (n int, err error) {
	err = c.stream.SendMsg(p)
	return len(p), err
}

func (c rawConn) LocalAddr() net.Addr {
	return nil
}

func (c rawConn) RemoteAddr() net.Addr {
	return nil
}

func (c rawConn) SetDeadline(t time.Time) error {
	return nil
}

func (c rawConn) Close() error {
	return c.closer()
}
