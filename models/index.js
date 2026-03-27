const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
  userId: { type: String, unique: true },
  phone: { type: String, unique: true },
  name: String,
  role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
  isOnline: { type: Boolean, default: false },
  isChatOnline: { type: Boolean, default: false },
  isAudioOnline: { type: Boolean, default: false },
  isVideoOnline: { type: Boolean, default: false },
  isBanned: { type: Boolean, default: false },
  skills: [String],
  price: { type: Number, default: 20 },
  walletBalance: { type: Number, default: 108 },
  totalEarnings: { type: Number, default: 0 },
  experience: { type: Number, default: 0 },
  isVerified: { type: Boolean, default: false },
  isDocumentVerified: { type: Boolean, default: false },
  documentStatus: { type: String, enum: ['none', 'processing', 'verified'], default: 'none' },
  image: { type: String, default: '' },
  birthDetails: {
    dob: String, tob: String, pob: String, lat: Number, lon: Number
  },
  intakeDetails: {
    gender: String, marital: String, occupation: String, topic: String,
    partner: { name: String, dob: String, tob: String, pob: String }
  },
  isAvailable: { type: Boolean, default: false },
  isBusy: { type: Boolean, default: false },
  availabilityExpiresAt: Date,
  fcmToken: String,
  lastSeen: { type: Date, default: Date.now },
  referredBy: { type: String, default: null },
  referralCode: { type: String, unique: true, sparse: true },
  hasRecharged: { type: Boolean, default: false },
  referralEarnings: { type: Number, default: 0 },
  referralWithdrawn: { type: Number, default: 0 },
  astrologerRequestStatus: { type: String, enum: ['none', 'pending', 'approved', 'rejected'], default: 'none' },
  astrologerRequestedAt: Date,
  astrologerExperience: String,
  astrologerAbout: String,
  astrologerSkills: [String],
  bankDetails: {
    accountHolder: String, accountNumber: String, bankName: String,
    ifscCode: String, upiId: String
  }
  },
  pendingImage: { type: String, default: '' },
  photoStatus: { type: String, enum: ['none', 'pending', 'approved', 'rejected'], default: 'none' }
});

const CallRequestSchema = new mongoose.Schema({
  callId: { type: String, unique: true },
  callerId: String,
  receiverId: String,
  status: { type: String, enum: ['initiated', 'ringing', 'accepted', 'rejected', 'missed'], default: 'initiated' },
  createdAt: { type: Date, default: Date.now }
});

const SessionSchema = new mongoose.Schema({
  sessionId: { type: String, unique: true },
  clientId: String,
  astrologerId: String,
  clientConnectedAt: Number,
  astrologerConnectedAt: Number,
  actualBillingStart: Number,
  sessionEndAt: Number,
  status: { type: String, enum: ['active', 'ended'], default: 'active' },
  fromUserId: String, toUserId: String, type: String,
  duration: Number, totalEarned: Number, totalDeducted: Number
});

const PairMonthSchema = new mongoose.Schema({
  pairId: { type: String, required: true, index: true },
  clientId: String,
  astrologerId: String,
  yearMonth: { type: String, required: true },
  currentSlab: { type: Number, default: 0 },
  slabLockedAt: { type: Number, default: 0 },
  resetAt: Date
});
PairMonthSchema.index({ pairId: 1, yearMonth: 1 }, { unique: true });

const BillingLedgerSchema = new mongoose.Schema({
  billingId: { type: String, unique: true },
  sessionId: { type: String, required: true, index: true },
  minuteIndex: { type: Number, required: true },
  chargedToClient: Number,
  creditedToAstrologer: Number,
  adminAmount: Number,
  reason: {
    type: String,
    enum: ['first_60', 'first_60_partial', 'slab', 'rounded', 'payout_withdrawal', 'referral', 'bonus',
      'slab_1', 'slab_2', 'slab_3', 'slab_4', 'slab_5', 'slab_6', 'slab_7', 'slab_8', 'slab_9', 'slab_10',
      'slab_11', 'slab_12', 'slab_13', 'slab_14', 'slab_15', 'slab_16', 'slab_17', 'slab_18', 'slab_19', 'slab_20']
  },
  createdAt: { type: Date, default: Date.now }
});

const WithdrawalSchema = new mongoose.Schema({
  astroId: String,
  amount: Number,
  type: { type: String, enum: ['payout', 'referral'], default: 'payout' },
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date
});

const PaymentSchema = new mongoose.Schema({
  transactionId: { type: String, unique: true },
  merchantTransactionId: String,
  userId: String,
  amount: Number,
  status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
  createdAt: { type: Date, default: Date.now },
  providerRefId: String,
  isApp: { type: Boolean, default: false }
});

const ChatMessageSchema = new mongoose.Schema({
  messageId: { type: String, unique: true },
  sessionId: String,
  fromUserId: String,
  toUserId: String,
  text: String,
  type: { type: String, default: 'text' },
  timestamp: { type: Number, default: Date.now },
  createdAt: { type: Date, default: Date.now }
});

const AcademyVideoSchema = new mongoose.Schema({
  title: String,
  youtubeUrl: String,
  thumbnail: String,
  category: String,
  createdAt: { type: Date, default: Date.now }
});

const BannerSchema = new mongoose.Schema({
  imageUrl: { type: String, required: true },
  title: String,
  subtitle: String,
  ctaText: { type: String, default: 'Learn More' },
  order: { type: Number, default: 0 },
  isActive: { type: Boolean, default: true },
  createdAt: { type: Date, default: Date.now }
});

const AccountDeletionRequestSchema = new mongoose.Schema({
  requestId: { type: String, unique: true },
  userIdentifier: { type: String, required: true },
  userId: String,
  reason: String,
  status: { type: String, default: 'pending' },
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: String,
  notes: String
});

const AstrologerApplicationSchema = new mongoose.Schema({
  applicationId: { type: String, unique: true },
  realName: { type: String, required: true },
  displayName: String,
  gender: String,
  dob: String,
  tob: String,
  pob: String,
  cellNumber1: { type: String, required: true },
  cellNumber2: String,
  whatsAppNumber: String,
  email: String,
  address: String,
  aadharNumber: String,
  panNumber: String,
  astrologyExperience: String,
  profession: String,
  bankDetails: String,
  upiName: String,
  upiNumber: String,
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  appliedAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: String,
  notes: String
});

const NotificationSchema = new mongoose.Schema({
  userId: String,
  type: { type: String, default: 'system' },
  title: String,
  message: String,
  details: Object,
  read: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now }
});

const ReviewSchema = new mongoose.Schema({
  sessionId: String,
  clientId: String,
  astrologerId: String,
  rating: { type: Number, min: 1, max: 5 },
  comment: String,
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  createdAt: { type: Date, default: Date.now }
});

const GlobalSettingsSchema = new mongoose.Schema({
  key: { type: String, unique: true },
  value: mongoose.Schema.Types.Mixed,
  updatedAt: { type: Date, default: Date.now }
});

const SystemLogSchema = new mongoose.Schema({
  type: { type: String, enum: ['info', 'warn', 'error', 'critical'], default: 'info' },
  module: String,
  message: String,
  details: Object,
  timestamp: { type: Date, default: Date.now }
});
SystemLogSchema.index({ timestamp: 1 }, { expireAfterSeconds: 15 * 24 * 60 * 60 }); // 15 days TTL

module.exports = {
  User: mongoose.model('User', UserSchema),
  CallRequest: mongoose.model('CallRequest', CallRequestSchema),
  Session: mongoose.model('Session', SessionSchema),
  PairMonth: mongoose.model('PairMonth', PairMonthSchema),
  BillingLedger: mongoose.model('BillingLedger', BillingLedgerSchema),
  Withdrawal: mongoose.model('Withdrawal', WithdrawalSchema),
  Payment: mongoose.model('Payment', PaymentSchema),
  ChatMessage: mongoose.model('ChatMessage', ChatMessageSchema),
  AcademyVideo: mongoose.model('AcademyVideo', AcademyVideoSchema),
  Banner: mongoose.model('Banner', BannerSchema),
  AccountDeletionRequest: mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema),
  AstrologerApplication: mongoose.model('AstrologerApplication', AstrologerApplicationSchema),
  Notification: mongoose.model('Notification', NotificationSchema)
  Notification: mongoose.model('Notification', NotificationSchema),
  Review: mongoose.model('Review', ReviewSchema),
  GlobalSettings: mongoose.model('GlobalSettings', GlobalSettingsSchema),
  SystemLog: mongoose.model('SystemLog', SystemLogSchema)
};
