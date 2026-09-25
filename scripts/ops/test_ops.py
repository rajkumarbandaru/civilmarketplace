"""Unit tests for the restore tooling's pure parts (no Docker needed).

    python3 -m unittest scripts/ops/test_ops.py
"""
import os, sys, unittest
from unittest import mock
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
sys.path.insert(0, os.path.dirname(__file__))
import ops, tenant_backup, tenant_restore


class IdentifiersTest(unittest.TestCase):
    def test_only_plain_identifiers_reach_sql(self):
        self.assertEqual(ops.ident("civil_engineer_users_acme__r120501"), "civil_engineer_users_acme__r120501")
        for bad in ["users`; DROP DATABASE x; --", "a b", "x" * 65, "", "acme'"]:
            with self.assertRaises(ValueError):
                ops.ident(bad)


class BinlogPositionTest(unittest.TestCase):
    def test_both_mysql_80_and_84_dump_headers_are_read(self):
        m = tenant_backup.POSITION.search(b"-- CHANGE MASTER TO MASTER_LOG_FILE='binlog.000035', MASTER_LOG_POS=9550055;")
        self.assertEqual((m.group(1), m.group(2)), (b"binlog.000035", b"9550055"))
        m = tenant_backup.POSITION.search(
            b"-- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='binlog.000002', SOURCE_LOG_POS=157;")
        self.assertEqual((m.group(1), m.group(2)), (b"binlog.000002", b"157"))


class RestoreChoiceTest(unittest.TestCase):
    backups = [{"id": "1", "takenAt": "2026-09-24 10:00:00", "cluster": "cluster-a", "schemas": {}},
               {"id": "2", "takenAt": "2026-09-24 12:00:00", "cluster": "cluster-a", "schemas": {}}]

    def test_there_must_be_a_backup_before_the_moment(self):
        with mock.patch.object(tenant_restore, "placement", return_value=("cluster-a", "mysql", "civil_mysql")), \
                mock.patch.object(tenant_restore, "backups", return_value=self.backups):
            with self.assertRaises(SystemExit) as e:
                tenant_restore.restore_side("acme", "2026-09-24 09:00:00")
            self.assertIn("no backup", str(e.exception))

    def test_a_backup_from_before_a_move_is_refused(self):
        with mock.patch.object(tenant_restore, "placement", return_value=("cluster-b", "mysql-b", "civil_mysql_b")), \
                mock.patch.object(tenant_restore, "backups", return_value=self.backups):
            with self.assertRaises(SystemExit) as e:
                tenant_restore.restore_side("acme", "2026-09-24 13:00:00")
            self.assertIn("now on cluster-b", str(e.exception))

    def test_the_latest_backup_before_the_moment_is_used(self):
        calls = []
        with mock.patch.object(tenant_restore, "placement", return_value=("cluster-a", "mysql", "civil_mysql")), \
                mock.patch.object(tenant_restore, "backups", return_value=self.backups), \
                mock.patch.object(tenant_restore, "root_pw", return_value="pw"):
            r = tenant_restore.restore_side("acme", "2026-09-24 11:00:00")
        self.assertEqual(r["backup"], "1")


class SwapTest(unittest.TestCase):
    def test_writes_are_resumed_even_if_the_swap_fails(self):
        events = []
        restore = {"id": "1", "container": "civil_mysql", "schemas": {"civil_engineer_users_acme": "civil_engineer_users_acme__r1"}}
        with mock.patch.object(tenant_restore, "mysql", side_effect=RuntimeError("disk full")), \
                mock.patch.object(tenant_restore, "tables", return_value=[]):
            with self.assertRaises(RuntimeError):
                tenant_restore.swap(restore, lambda: events.append("pause"), lambda: events.append("resume"))
        self.assertEqual(events, ["pause", "resume"])


if __name__ == "__main__":
    unittest.main()
