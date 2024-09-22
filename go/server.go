package _go

import (
	"context"
	"fmt"
	"net"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func RegisterServer(server *grpc.Server, fn func(context.Context, *grpc.ClientConn) error) {
	server.RegisterService(
		&grpc.ServiceDesc{
			ServiceName: "io.grpc.Tunnel",
			Streams: []grpc.StreamDesc{{
				StreamName:    "new",
				ClientStreams: true,
				ServerStreams: true,
				Handler: func(srv interface{}, stream grpc.ServerStream) error {
					client, err := grpc.NewClient(
						"0.0.0.0:0",
						grpc.WithTransportCredentials(insecure.NewCredentials()),
						grpc.WithContextDialer(func(ctx context.Context, s string) (net.Conn, error) {
							var r net.Conn = &rawConn{
								stream: stream,
								closer: func() error { return nil },
							}
							return r, nil
						}),
					)

					if err != nil {
						return fmt.Errorf("failed to create client: %w", err)
					}

					ctx, cancel := context.WithCancelCause(stream.Context())

					go func() {
						err := fn(ctx, client)
						cancel(err)
					}()

					<-ctx.Done()
					return ctx.Err()
				},
			}},
		},
		nil,
	)
}
