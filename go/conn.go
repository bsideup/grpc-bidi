package _go

import (
	"bytes"
	"net"
	"time"

	"google.golang.org/grpc"
)

type rawConn struct {
	stream grpc.Stream
	closer func() error
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

func (c rawConn) Close() error {
	return c.closer()
}

func (c rawConn) SetReadDeadline(_ time.Time) error {
	return nil
}

func (c rawConn) SetWriteDeadline(_ time.Time) error {
	return nil
}

func (c rawConn) LocalAddr() net.Addr {
	return nil
}

func (c rawConn) RemoteAddr() net.Addr {
	return nil
}

func (c rawConn) SetDeadline(_ time.Time) error {
	return nil
}
