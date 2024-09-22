package _go

import (
	"context"
	"errors"
	"net"
	"time"

	"google.golang.org/grpc"
)

type connListener struct {
	conn           *grpc.ClientConn
	currentChannel *rawConn
}

func NewListener(conn *grpc.ClientConn) net.Listener {
	return &connListener{conn: conn}
}

func (cl *connListener) Accept() (net.Conn, error) {
	if currentChannel := cl.currentChannel; currentChannel != nil {
		<-currentChannel.stream.Context().Done()
		time.Sleep(1 * time.Second)
	}

	s, err := cl.conn.NewStream(
		context.Background(),
		&grpc.StreamDesc{StreamName: "bidi", ClientStreams: true, ServerStreams: true},
		"/io.grpc.Tunnel/new",
		grpc.ForceCodec(&RawCodec{}),
		grpc.CallContentSubtype("raw"),
	)
	if err != nil {
		return nil, err
	}

	cl.currentChannel = &rawConn{
		closer: s.CloseSend,
		stream: s,
	}

	return cl.currentChannel, nil
}

func (cl *connListener) Close() error {
	var err error
	if currentChannel := cl.currentChannel; currentChannel != nil {
		err = currentChannel.Close()
	}
	return errors.Join(err, cl.conn.Close())
}

func (cl *connListener) Addr() net.Addr {
	return &net.TCPAddr{IP: net.IPv4(0, 0, 0, 0), Port: 0}
}
