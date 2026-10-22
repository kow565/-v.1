import unittest
from jev_bridge import email_triage, trade_review


class JevBridgeTests(unittest.TestCase):
    def test_email_prompt_and_classification(self):
        def fake(payload):
            self.assertEqual(payload['model'], 'jev-1.13.0')
            self.assertEqual(payload['state']['subject'], '중간고사 일정')
            self.assertNotIn('my-secret', str(payload))
            return {'model': 'jev-1.13.0', 'answers': {
                'category': {'choice': 'school', 'confidence': 0.94},
                'requires_action': {'noul': 0.9},
                'deadline': {'noul': 0.8}}}
        result = email_triage({'subject': '중간고사 일정', 'body': 'api_key: my-secret'}, transport=fake)
        self.assertEqual(result['route'], 'school')
        self.assertFalse(result['automatic_action'])

    def test_otp_bypasses_without_network(self):
        result = email_triage({'subject': '인증번호', 'body': '123456'},
                              transport=lambda _: self.fail('Never call network'))
        self.assertEqual(result['status'], 'bypass')

    def test_uncertainty_falls_back_to_existing_pipeline(self):
        result = email_triage({'subject': 'hello'}, transport=lambda _: {'answers': {
            'category': {'choice': 'other', 'confidence': 0.44},
            'requires_action': {'noul': 0.2}, 'deadline': {'noul': 0.1}}})
        self.assertEqual(result['status'], 'review')
        self.assertEqual(result['route'], 'existing_pipeline')

    def test_trade_is_never_authorized(self):
        def fake(payload):
            self.assertNotIn('SUPER_SECRET', str(payload))
            self.assertNotIn('private_key', str(payload))
            return {'answers': {
                'signal_quality': {'choice': 'review_candidate', 'confidence': .96},
                'signal_risk': {'score': 0.5}}}
        result = trade_review({'symbol': 'BTCUSDT', 'side': 'BUY',
                               'private_key': 'SUPER_SECRET',
                               'indicators': {'rsi': 42, 'api_key': 'SUPER_SECRET'}}, transport=fake)
        self.assertFalse(result['order_authorized'])
        self.assertTrue(result['approval_required'])
        self.assertTrue(result['is_confident_review_candidate'])

    def test_transport_failure_is_safe(self):
        def broken(_):
            raise RuntimeError('offline')
        self.assertEqual(email_triage({'subject': 'Hi'}, transport=broken)['route'], 'existing_pipeline')
        self.assertFalse(trade_review({'symbol': 'BTCUSDT'}, transport=broken)['order_authorized'])


if __name__ == '__main__':
    unittest.main()
