package main

import (
	"bytes"
	"context"
	"net"
	"time"

	"google.golang.org/grpc"
)

type RawCodec struct{}

func (c *RawCodec) Marshal(v interface{}) ([]byte, error) {
	return v.([]byte), nil
}

func (c *RawCodec) Unmarshal(data []byte, v interface{}) error {
	result := v.(*bytes.Buffer)

	result.Reset()
	_, err := result.Write(data)
	result.Truncate(len(data))
	return err
}

func (c *RawCodec) Name() string {
	return "raw"
}

type ClientChannelConn struct {
	stream grpc.ClientStream
}

var _ net.Conn = &ClientChannelConn{}

func (c *ClientChannelConn) SetReadDeadline(t time.Time) error {
	return nil
}

func (c *ClientChannelConn) SetWriteDeadline(t time.Time) error {
	return nil
}

func (c *ClientChannelConn) Read(p []byte) (n int, err error) {
	buf := bytes.NewBuffer(p)
	err = c.stream.RecvMsg(buf)

	return buf.Len(), err
}

func (c *ClientChannelConn) Write(p []byte) (n int, err error) {
	err = c.stream.SendMsg(p)
	return len(p), err
}

func (c *ClientChannelConn) Close() error {
	return c.stream.CloseSend()
}

func (c *ClientChannelConn) LocalAddr() net.Addr {
	return nil
}

func (c *ClientChannelConn) RemoteAddr() net.Addr {
	return nil
}

func (c *ClientChannelConn) SetDeadline(t time.Time) error {
	return nil
}

type ConnListener struct {
	conn           *grpc.ClientConn
	currentChannel *ClientChannelConn
}

func (cl *ConnListener) Accept() (net.Conn, error) {
	if currentChannel := cl.currentChannel; currentChannel != nil {
		<-currentChannel.stream.Context().Done()
		time.Sleep(1 * time.Second)
	}

	s, err := cl.conn.NewStream(
		context.Background(),
		&grpc.StreamDesc{StreamName: "new", ClientStreams: true, ServerStreams: true},
		"/io.grpc.Tunnel/new",
		grpc.ForceCodec(&RawCodec{}),
	)
	if err != nil {
		return nil, err
	}

	cl.currentChannel = &ClientChannelConn{stream: s}

	return cl.currentChannel, nil
}

func (cl *ConnListener) Close() error {
	if currentChannel := cl.currentChannel; currentChannel != nil {
		currentChannel.Close()
	}
	return cl.conn.Close()
}

func (cl *ConnListener) Addr() net.Addr {
	return &net.TCPAddr{IP: net.IPv4(0, 0, 0, 0), Port: 0}
}
