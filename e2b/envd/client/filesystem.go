package client

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"connectrpc.com/connect"
	"github.com/openkruise/agents-api/e2b/envd/filesystem"
	"github.com/openkruise/agents-api/e2b/envd/filesystem/filesystemconnect"
)

// EntryType represents the type of a filesystem entry.
type EntryType string

const (
	EntryTypeFile    EntryType = "file"
	EntryTypeDir     EntryType = "directory"
	EntryTypeSymlink EntryType = "symlink"
)

// EntryInfo represents information about a filesystem entry.
type EntryInfo struct {
	Name          string
	Type          EntryType
	Path          string
	Size          int64
	Mode          uint32
	Permissions   string
	Owner         string
	Group         string
	ModifiedTime  time.Time
	SymlinkTarget *string
}

// Filesystem provides filesystem operations in the sandbox over the official
// envd Filesystem gRPC service.
type Filesystem struct {
	Rpc     filesystemconnect.FilesystemClient
	headers map[string]string
}

// NewFilesystem creates a new Filesystem instance backed by the envd gRPC client.
func NewFilesystem(rpc filesystemconnect.FilesystemClient, headers map[string]string) *Filesystem {
	return &Filesystem{
		Rpc:     rpc,
		headers: headers,
	}
}

// List lists entries in a directory.
func (f *Filesystem) List(ctx context.Context, path string, depth ...int32) ([]EntryInfo, error) {
	d := uint32(1)
	if len(depth) > 0 && depth[0] >= 1 {
		d = uint32(depth[0])
	}

	req := connect.NewRequest(&filesystem.ListDirRequest{
		Path:  path,
		Depth: d,
	})
	f.setRPCHeaders(req)

	resp, err := f.Rpc.ListDir(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("failed to list directory: %w", err)
	}

	entries := make([]EntryInfo, 0, len(resp.Msg.Entries))
	for _, entry := range resp.Msg.Entries {
		entryType := mapFileType(entry.Type)
		if entryType == "" {
			continue
		}
		entries = append(entries, convertEntryInfo(entry))
	}

	return entries, nil
}

// Exists checks if a file or directory exists.
func (f *Filesystem) Exists(ctx context.Context, path string) (bool, error) {
	req := connect.NewRequest(&filesystem.StatRequest{Path: path})
	f.setRPCHeaders(req)

	_, err := f.Rpc.Stat(ctx, req)
	if err != nil {
		if connectErr, ok := err.(*connect.Error); ok && connectErr.Code() == connect.CodeNotFound {
			return false, nil
		}
		return false, fmt.Errorf("failed to check existence: %w", err)
	}

	return true, nil
}

// GetInfo returns information about a file or directory.
func (f *Filesystem) GetInfo(ctx context.Context, path string) (*EntryInfo, error) {
	req := connect.NewRequest(&filesystem.StatRequest{Path: path})
	f.setRPCHeaders(req)

	resp, err := f.Rpc.Stat(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("failed to get file info: %w", err)
	}

	info := convertEntryInfo(resp.Msg.Entry)
	return &info, nil
}

// Remove removes a file or directory.
func (f *Filesystem) Remove(ctx context.Context, path string) error {
	req := connect.NewRequest(&filesystem.RemoveRequest{Path: path})
	f.setRPCHeaders(req)

	_, err := f.Rpc.Remove(ctx, req)
	if err != nil {
		return fmt.Errorf("failed to remove: %w", err)
	}

	return nil
}

// Rename renames/moves a file or directory.
func (f *Filesystem) Rename(ctx context.Context, oldPath, newPath string) (*EntryInfo, error) {
	req := connect.NewRequest(&filesystem.MoveRequest{
		Source:      oldPath,
		Destination: newPath,
	})
	f.setRPCHeaders(req)

	resp, err := f.Rpc.Move(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("failed to rename: %w", err)
	}

	info := convertEntryInfo(resp.Msg.Entry)
	return &info, nil
}

// MakeDir creates a new directory (and all parent directories if needed).
func (f *Filesystem) MakeDir(ctx context.Context, path string) (bool, error) {
	req := connect.NewRequest(&filesystem.MakeDirRequest{Path: path})
	f.setRPCHeaders(req)

	_, err := f.Rpc.MakeDir(ctx, req)
	if err != nil {
		if connectErr, ok := err.(*connect.Error); ok && connectErr.Code() == connect.CodeAlreadyExists {
			return false, nil
		}
		return false, fmt.Errorf("failed to make directory: %w", err)
	}

	return true, nil
}

func mapFileType(ft filesystem.FileType) EntryType {
	switch ft {
	case filesystem.FileType_FILE_TYPE_FILE:
		return EntryTypeFile
	case filesystem.FileType_FILE_TYPE_DIRECTORY:
		return EntryTypeDir
	case filesystem.FileType_FILE_TYPE_SYMLINK:
		return EntryTypeSymlink
	default:
		return ""
	}
}

func convertEntryInfo(entry *filesystem.EntryInfo) EntryInfo {
	info := EntryInfo{
		Name:        entry.Name,
		Type:        mapFileType(entry.Type),
		Path:        entry.Path,
		Size:        entry.Size,
		Mode:        entry.Mode,
		Permissions: entry.Permissions,
		Owner:       entry.Owner,
		Group:       entry.Group,
	}

	if entry.ModifiedTime != nil {
		info.ModifiedTime = entry.ModifiedTime.AsTime()
	}

	if entry.SymlinkTarget != nil {
		target := *entry.SymlinkTarget
		info.SymlinkTarget = &target
	}

	return info
}

func (f *Filesystem) setRPCHeaders(req interface{ Header() http.Header }) {
	for k, v := range f.headers {
		req.Header().Set(k, v)
	}
}
