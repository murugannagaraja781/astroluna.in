/**
 * Seed 32 Dummy Astrologers into MongoDB
 * Run: node seed-astrologers.js
 */
require('dotenv').config();
const mongoose = require('mongoose');
const crypto = require('crypto');

const MONGODB_URI = process.env.MONGODB_URI;

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
        dob: String,
        tob: String,
        pob: String,
        lat: Number,
        lon: Number
    },
    intakeDetails: {
        gender: String,
        marital: String,
        occupation: String,
        topic: String,
        partner: {
            name: String,
            dob: String,
            tob: String,
            pob: String
        }
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
    astrologerExperience: String
}, { timestamps: true });

const User = mongoose.model('User', UserSchema);

// ============ 32 Realistic Astrologer Profiles ============
const astrologers = [
    {
        name: "Pandit Raghunath Sharma",
        phone: "9100000001",
        skills: ["Vedic Astrology", "Kundli Reading", "Marriage Compatibility", "Career Guidance"],
        price: 15,
        experience: 22,
        image: "https://ui-avatars.com/api/?name=Raghunath+Sharma&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "22 years in Vedic Jyotish Shastra. Specializing in Kundli matching and Dasha predictions."
    },
    {
        name: "Jyotishi Lakshmi Devi",
        phone: "9100000002",
        skills: ["Nadi Astrology", "Palmistry", "Vastu Shastra", "Gemstone Recommendation"],
        price: 12,
        experience: 18,
        image: "https://ui-avatars.com/api/?name=Lakshmi+Devi&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "18 years of Nadi Jyothidam practice from hereditary Nadi family."
    },
    {
        name: "Acharya Subramaniam",
        phone: "9100000003",
        skills: ["KP Astrology", "Horary Astrology", "Medical Astrology", "Financial Astrology"],
        price: 25,
        experience: 30,
        image: "https://ui-avatars.com/api/?name=Subramaniam&background=059669&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "30 years in KP System. Former professor at Indian Council of Astrological Sciences."
    },
    {
        name: "Guruji Venkatesh Iyer",
        phone: "9100000004",
        skills: ["Vedic Astrology", "Muhurtha", "Panchanga Reading", "Remedial Astrology"],
        price: 20,
        experience: 25,
        image: "https://ui-avatars.com/api/?name=Venkatesh+Iyer&background=D97706&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "25 years specializing in Muhurtha selection and temple-based remedies."
    },
    {
        name: "Dr. Meenakshi Sundaram",
        phone: "9100000005",
        skills: ["Medical Astrology", "Prashna Kundli", "Vedic Astrology", "Child Astrology"],
        price: 30,
        experience: 28,
        image: "https://ui-avatars.com/api/?name=Meenakshi+S&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "PhD in Jyotish Shastra. 28 years experience in medical and child astrology."
    },
    {
        name: "Pandit Arvind Mishra",
        phone: "9100000006",
        skills: ["Lal Kitab", "Vedic Astrology", "Vastu Shastra", "Numerology"],
        price: 10,
        experience: 15,
        image: "https://ui-avatars.com/api/?name=Arvind+Mishra&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "15 years in Lal Kitab remedies and Vastu consultation."
    },
    {
        name: "Jyotish Acharya Savitri",
        phone: "9100000007",
        skills: ["Tarot Reading", "Vedic Astrology", "Love Astrology", "Relationship Counseling"],
        price: 18,
        experience: 12,
        image: "https://ui-avatars.com/api/?name=Savitri&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "12 years combining Tarot and Vedic wisdom for relationship guidance."
    },
    {
        name: "Maharaj Krishnananda",
        phone: "9100000008",
        skills: ["Spiritual Astrology", "Meditation Guidance", "Karma Reading", "Past Life Analysis"],
        price: 35,
        experience: 35,
        image: "https://ui-avatars.com/api/?name=Krishnananda&background=059669&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "35 years of spiritual astrology. Ashram-trained in Rishikesh."
    },
    {
        name: "Pandit Ramesh Joshi",
        phone: "9100000009",
        skills: ["Vedic Astrology", "Kundli Matching", "Business Astrology", "Muhurtha"],
        price: 8,
        experience: 10,
        image: "https://ui-avatars.com/api/?name=Ramesh+Joshi&background=D97706&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "10 years experience in business and marriage astrology."
    },
    {
        name: "Dr. Annapurna Shastri",
        phone: "9100000010",
        skills: ["Nadi Astrology", "Vedic Astrology", "Health Predictions", "Career Guidance"],
        price: 22,
        experience: 20,
        image: "https://ui-avatars.com/api/?name=Annapurna+S&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "20 years in Nadi astrology. Published 3 books on Jyotish."
    },
    {
        name: "Guru Shanmugam Pillai",
        phone: "9100000011",
        skills: ["Siddha Astrology", "Nadi Reading", "Gemstone Therapy", "Temple Remedies"],
        price: 28,
        experience: 32,
        image: "https://ui-avatars.com/api/?name=Shanmugam+P&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "32 years in Siddha tradition. Trained in ancient Tamil Nadi leaves."
    },
    {
        name: "Pandit Devendra Sharma",
        phone: "9100000012",
        skills: ["Vedic Astrology", "Vastu Shastra", "Numerology", "Face Reading"],
        price: 14,
        experience: 16,
        image: "https://ui-avatars.com/api/?name=Devendra+S&background=059669&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "16 years in Vedic astrology with additional expertise in face reading."
    },
    {
        name: "Jyotish Prema Kumari",
        phone: "9100000013",
        skills: ["Tarot Reading", "Angel Card Reading", "Love Astrology", "Crystal Healing"],
        price: 16,
        experience: 8,
        image: "https://ui-avatars.com/api/?name=Prema+Kumari&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "8 years in Tarot and Angel card readings. Certified crystal healer."
    },
    {
        name: "Acharya Balasubramanian",
        phone: "9100000014",
        skills: ["KP Astrology", "Stellar Astrology", "Stock Market Astrology", "Electional Astrology"],
        price: 40,
        experience: 27,
        image: "https://ui-avatars.com/api/?name=Balasubramanian&background=D97706&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "27 years in KP System. Expert in financial and stock market predictions."
    },
    {
        name: "Swami Dharmananda",
        phone: "9100000015",
        skills: ["Spiritual Astrology", "Vedic Rituals", "Mantra Healing", "Chakra Balancing"],
        price: 25,
        experience: 24,
        image: "https://ui-avatars.com/api/?name=Dharmananda&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "24 years in spiritual counseling. Expert in Mantra healing and Vedic rituals."
    },
    {
        name: "Pandit Gopal Krishna",
        phone: "9100000016",
        skills: ["Vedic Astrology", "Kundli Reading", "Dasha Analysis", "Transit Predictions"],
        price: 12,
        experience: 14,
        image: "https://ui-avatars.com/api/?name=Gopal+Krishna&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "14 years specializing in Dasha and transit-based predictions."
    },
    {
        name: "Dr. Vasantha Kumari",
        phone: "9100000017",
        skills: ["Nadi Astrology", "Palmistry", "Numerology", "Compatibility Analysis"],
        price: 20,
        experience: 19,
        image: "https://ui-avatars.com/api/?name=Vasantha+K&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "19 years in Nadi and Palmistry. Known for precise compatibility readings."
    },
    {
        name: "Guruji Nagaraj Rao",
        phone: "9100000018",
        skills: ["Vedic Astrology", "Prashna Shastra", "Remedial Astrology", "Yantra Consultation"],
        price: 18,
        experience: 21,
        image: "https://ui-avatars.com/api/?name=Nagaraj+Rao&background=059669&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "21 years in Prashna Shastra. Specialist in Yantra-based remedies."
    },
    {
        name: "Pandit Harish Chandra",
        phone: "9100000019",
        skills: ["Lal Kitab", "Vedic Astrology", "Property Astrology", "Legal Astrology"],
        price: 15,
        experience: 17,
        image: "https://ui-avatars.com/api/?name=Harish+Chandra&background=D97706&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "17 years in Lal Kitab. Expert in property and legal matter predictions."
    },
    {
        name: "Jyotish Kamala Devi",
        phone: "9100000020",
        skills: ["Tarot Reading", "Rune Reading", "Psychic Reading", "Dream Interpretation"],
        price: 22,
        experience: 11,
        image: "https://ui-avatars.com/api/?name=Kamala+Devi&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "11 years in psychic and intuitive readings. Certified Tarot master."
    },
    {
        name: "Acharya Sundararajan",
        phone: "9100000021",
        skills: ["Vedic Astrology", "Agama Shastra", "Temple Astrology", "Festival Muhurtha"],
        price: 30,
        experience: 26,
        image: "https://ui-avatars.com/api/?name=Sundararajan&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "26 years as temple priest-astrologer. Agama Shastra expert."
    },
    {
        name: "Pandit Vishwanath Dubey",
        phone: "9100000022",
        skills: ["Vedic Astrology", "Vastu Shastra", "Graha Shanti", "Puja Recommendation"],
        price: 10,
        experience: 9,
        image: "https://ui-avatars.com/api/?name=Vishwanath+D&background=059669&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "9 years in Vedic astrology and Graha Shanti puja consultation."
    },
    {
        name: "Dr. Saraswathi Ammal",
        phone: "9100000023",
        skills: ["Nadi Astrology", "Medical Astrology", "Child Birth Prediction", "Education Astrology"],
        price: 25,
        experience: 23,
        image: "https://ui-avatars.com/api/?name=Saraswathi+A&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "23 years in Nadi astrology. PhD in Astro-Medicine from Varanasi."
    },
    {
        name: "Guru Thirunavukkarasu",
        phone: "9100000024",
        skills: ["Siddha Astrology", "Herbal Remedies", "Mantra Therapy", "Spiritual Healing"],
        price: 35,
        experience: 29,
        image: "https://ui-avatars.com/api/?name=Thirunavukkarasu&background=D97706&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "29 years in Siddha tradition. Combines herbal and astrological remedies."
    },
    {
        name: "Pandit Mahendra Singh",
        phone: "9100000025",
        skills: ["Vedic Astrology", "Government Job Prediction", "Competitive Exam Guidance", "Career Astrology"],
        price: 14,
        experience: 13,
        image: "https://ui-avatars.com/api/?name=Mahendra+Singh&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "13 years specializing in career and government job predictions."
    },
    {
        name: "Jyotish Padmavathi",
        phone: "9100000026",
        skills: ["Vedic Astrology", "Kundli Matching", "Love Compatibility", "Relationship Healing"],
        price: 16,
        experience: 10,
        image: "https://ui-avatars.com/api/?name=Padmavathi&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: false,
        astrologerExperience: "10 years in love and relationship astrology. Kundli matching expert."
    },
    {
        name: "Acharya Ramaswamy",
        phone: "9100000027",
        skills: ["KP Astrology", "Horary Astrology", "Lost Object Finding", "Travel Astrology"],
        price: 28,
        experience: 24,
        image: "https://ui-avatars.com/api/?name=Ramaswamy&background=059669&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "24 years in KP and Horary astrology. Known for precise timing predictions."
    },
    {
        name: "Swami Vivekananda Das",
        phone: "9100000028",
        skills: ["Spiritual Astrology", "Bhagavad Gita Counseling", "Karma Analysis", "Life Purpose Reading"],
        price: 20,
        experience: 20,
        image: "https://ui-avatars.com/api/?name=Vivekananda+D&background=D97706&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "20 years of spiritual counseling through Vedic astrology and Gita wisdom."
    },
    {
        name: "Pandit Satyanarayan",
        phone: "9100000029",
        skills: ["Vedic Astrology", "Panchanga", "Muhurtha Selection", "House Construction Timing"],
        price: 12,
        experience: 18,
        image: "https://ui-avatars.com/api/?name=Satyanarayan&background=1E40AF&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "18 years in Muhurtha and Panchanga-based predictions for all life events."
    },
    {
        name: "Dr. Kavitha Rangan",
        phone: "9100000030",
        skills: ["Western Astrology", "Vedic Astrology", "Psychology", "Relationship Counseling"],
        price: 22,
        experience: 15,
        image: "https://ui-avatars.com/api/?name=Kavitha+R&background=EC4899&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "15 years combining Western and Vedic systems. Masters in Psychology."
    },
    {
        name: "Guruji Muthukumar",
        phone: "9100000031",
        skills: ["Siddha Astrology", "Nadi Reading", "Agathiyar Nadi", "Ancestral Karma Reading"],
        price: 40,
        experience: 33,
        image: "https://ui-avatars.com/api/?name=Muthukumar&background=7C3AED&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "33 years in Agathiyar Nadi reading. 4th generation Nadi astrologer."
    },
    {
        name: "Pandit Chandrasekhar",
        phone: "9100000032",
        skills: ["Vedic Astrology", "Gemstone Recommendation", "Rudraksha Consultation", "Planetary Remedies"],
        price: 18,
        experience: 16,
        image: "https://ui-avatars.com/api/?name=Chandrasekhar&background=059669&color=fff&size=256&bold=true",
        isVerified: true,
        astrologerExperience: "16 years specializing in gemstone and Rudraksha-based astrological remedies."
    }
];

async function seed() {
    try {
        console.log("Connecting to MongoDB...");
        await mongoose.connect(MONGODB_URI);
        console.log("Connected!\n");

        let created = 0, skipped = 0;

        for (let i = 0; i < astrologers.length; i++) {
            const a = astrologers[i];
            const userId = `ASTRO_DEMO_${String(i + 1).padStart(3, '0')}`;
            const referralCode = `AST${crypto.randomBytes(3).toString('hex').toUpperCase()}`;

            // Check if already exists
            const existing = await User.findOne({ phone: a.phone });
            if (existing) {
                console.log(`  [SKIP] ${a.name} (phone ${a.phone} already exists)`);
                skipped++;
                continue;
            }

            // Randomize online status for demo variety
            const onlineChance = Math.random();
            const isOnline = onlineChance < 0.4; // ~40% online
            const isBusy = !isOnline && onlineChance < 0.55; // ~15% busy

            await User.create({
                userId,
                phone: a.phone,
                name: a.name,
                role: 'astrologer',
                isOnline: isOnline,
                isChatOnline: isOnline,
                isAudioOnline: isOnline,
                isVideoOnline: isOnline && Math.random() > 0.3,
                isBanned: false,
                skills: a.skills,
                price: a.price,
                walletBalance: Math.floor(Math.random() * 5000) + 500,
                totalEarnings: Math.floor(Math.random() * 100000) + 5000,
                experience: a.experience,
                isVerified: a.isVerified,
                isDocumentVerified: a.isVerified,
                documentStatus: a.isVerified ? 'verified' : 'processing',
                image: a.image,
                birthDetails: {
                    dob: `${1960 + Math.floor(Math.random() * 30)}-${String(Math.floor(Math.random() * 12) + 1).padStart(2, '0')}-${String(Math.floor(Math.random() * 28) + 1).padStart(2, '0')}`,
                    tob: `${String(Math.floor(Math.random() * 24)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}`,
                    pob: ['Chennai', 'Madurai', 'Coimbatore', 'Trichy', 'Salem', 'Varanasi', 'Delhi', 'Mumbai', 'Kolkata', 'Hyderabad'][Math.floor(Math.random() * 10)],
                    lat: 8.0 + Math.random() * 20,
                    lon: 72.0 + Math.random() * 15
                },
                isAvailable: isOnline,
                isBusy: isBusy,
                lastSeen: new Date(Date.now() - Math.floor(Math.random() * 86400000)),
                referralCode: referralCode,
                hasRecharged: true,
                astrologerRequestStatus: 'approved',
                astrologerRequestedAt: new Date(Date.now() - Math.floor(Math.random() * 30 * 86400000)),
                astrologerExperience: a.astrologerExperience
            });

            const status = isOnline ? '🟢 Online' : isBusy ? '🔴 Busy' : '⚫ Offline';
            console.log(`  [${String(i + 1).padStart(2)}] ✅ ${a.name} | ₹${a.price}/min | ${a.experience}yr | ${status} | ${a.skills.slice(0, 2).join(', ')}`);
            created++;
        }

        console.log(`\n========================================`);
        console.log(`  ✅ Created: ${created} astrologers`);
        console.log(`  ⏭️  Skipped: ${skipped} (already existed)`);
        console.log(`  📊 Total: ${created + skipped}`);
        console.log(`========================================\n`);

        await mongoose.disconnect();
        console.log("Database disconnected. Done!");
        process.exit(0);
    } catch (err) {
        console.error("Seed Error:", err);
        process.exit(1);
    }
}

seed();
